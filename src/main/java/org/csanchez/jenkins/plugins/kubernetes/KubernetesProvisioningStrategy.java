package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.LoadStatistics;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.model.Label;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static hudson.ExtensionList.lookup;
import static java.util.Objects.isNull;


@Extension(ordinal = 10)
public class KubernetesProvisioningStrategy extends NodeProvisioner.Strategy {
    private static final Logger LOGGER = Logger.getLogger(KubernetesProvisioningStrategy.class.getName());

    @Nonnull
    @Override
    public NodeProvisioner.StrategyDecision apply(@Nonnull NodeProvisioner.StrategyState strategyState) {
        LOGGER.info("Applying provisioning.");
        final Label label = strategyState.getLabel();
        LoadStatistics.LoadStatisticsSnapshot snapshot = strategyState.getSnapshot();

        for (KubernetesCloud k8sCloud: getK8SClouds()) {
            for (PodTemplate template: k8sCloud.getTemplates(label)) {
                int availableCapacity = snapshot.getAvailableExecutors() +
                        snapshot.getConnectingExecutors() +
                        strategyState.getAdditionalPlannedCapacity() +
                        strategyState.getPlannedCapacitySnapshot();
                int currentDemand = snapshot.getQueueLength();
                LOGGER.log(Level.INFO, "Available capacity={0}, currentDemand={1}", new Object[] {availableCapacity, currentDemand});
                if (availableCapacity < currentDemand) {

                    Collection<PlannedNode> plannedNodes = k8sCloud.provision(label, currentDemand - availableCapacity);
                    LOGGER.log(Level.INFO, "Planned {0} new nodes", plannedNodes.size());
                    strategyState.recordPendingLaunches(plannedNodes);
                    availableCapacity += plannedNodes.size();
                    LOGGER.log(Level.INFO, "After {0} provisioning, available capacity={1}, currentDemand={2}",
                            new Object[] {k8sCloud.getName(), availableCapacity, currentDemand});

                }

                if (availableCapacity >= currentDemand) {
                    LOGGER.info("Provisioning completed");
                    return NodeProvisioner.StrategyDecision.PROVISIONING_COMPLETED;
                } else {
                    LOGGER.warning("Provisioning not complete try next template");
                }
            }
            LOGGER.warning("Provisioning not complete try next kubernetes cloud");

        }
        LOGGER.warning("Provisioning not complete, consulting remaining strategies");
        return NodeProvisioner.StrategyDecision.CONSULT_REMAINING_STRATEGIES;
    }

    @Nonnull
    public static synchronized List<KubernetesCloud> getK8SClouds() {
        return Jenkins.getActiveInstance().clouds.stream()
                .filter(Objects::nonNull)
                .filter(KubernetesCloud.class::isInstance)
                .map(cloud -> (KubernetesCloud) cloud)
                .collect(Collectors.toList());
    }
}
