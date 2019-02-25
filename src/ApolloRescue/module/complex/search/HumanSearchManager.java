package ApolloRescue.module.complex.search;

import ApolloRescue.ApolloConstants;
import ApolloRescue.module.universal.ApolloWorld;
import ApolloRescue.module.universal.entities.Path;
import adf.agent.action.common.ActionMove;
import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.algorithm.Clustering;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.Search;
import com.mrl.debugger.remote.VDClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static rescuecore2.standard.entities.StandardEntityURN.AMBULANCE_TEAM;
import static rescuecore2.standard.entities.StandardEntityURN.FIRE_BRIGADE;
import static rescuecore2.standard.entities.StandardEntityURN.POLICE_FORCE;

public class HumanSearchManager extends Search {
    private static Log logger = LogFactory.getLog(HumanSearchManager.class);
    private final ApolloWorld world;
    private Area targetArea;
    private EntityID target;
    private HumanSearchMethod humanSearchMethod;
    private SimpleSearchMethod simpleSearchMethod;
    private int lastUpdateTime;
    private boolean greedyStrategy;
    private int thisCycleExecute;
    private int lastExecuteTime;
    private boolean needChange;
    private PathPlanning pathPlanning;
    private Clustering clustering;
    private final HumanSearchStrategy searchStrategy;

    public HumanSearchManager(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        targetArea = null;
        target = null;
        greedyStrategy = false;
        lastUpdateTime = 0;
        lastExecuteTime = 0;
        thisCycleExecute = 0;
        needChange = false;

        StandardEntityURN agentURN = ai.me().getStandardURN();
        switch (si.getMode()) {
            case PRECOMPUTATION_PHASE:
                if (agentURN == AMBULANCE_TEAM) {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Ambulance", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Ambulance", "adf.sample.module.algorithm.SampleKMeans");
                } else if (agentURN == FIRE_BRIGADE) {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Fire", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Fire", "adf.sample.module.algorithm.SampleKMeans");
                } else if (agentURN == POLICE_FORCE) {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Police", "adf.sample.module.algorithm.SamplePathPlanning");
//                    this.clustering = moduleManager.getModule("ApolloSimpleFireSearch.Clustering.Police", "adf.sample.module.algorithm.SampleKMeans");
                    this.clustering = moduleManager.getModule("RoadDetector.Clustering.Police", "adf.sample.module.algorithm.SampleKMeans");

                }
                break;
            case PRECOMPUTED:
                if (agentURN == AMBULANCE_TEAM) {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Ambulance", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Ambulance", "adf.sample.module.algorithm.SampleKMeans");
                } else if (agentURN == FIRE_BRIGADE) {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Fire", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Fire", "adf.sample.module.algorithm.SampleKMeans");
                } else if (agentURN == POLICE_FORCE) {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Police", "adf.sample.module.algorithm.SamplePathPlanning");
//                    this.clustering = moduleManager.getModule("ApolloSimpleFireSearch.Clustering.Police", "adf.sample.module.algorithm.SampleKMeans");
                    this.clustering = moduleManager.getModule("RoadDetector.Clustering.Police", "adf.sample.module.algorithm.SampleKMeans");

                }
                break;
            case NON_PRECOMPUTE:
                if (agentURN == AMBULANCE_TEAM) {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Ambulance", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Ambulance", "adf.sample.module.algorithm.SampleKMeans");
                } else if (agentURN == FIRE_BRIGADE) {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Fire", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Fire", "adf.sample.module.algorithm.SampleKMeans");
                } else if (agentURN == POLICE_FORCE) {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Police", "adf.sample.module.algorithm.SamplePathPlanning");
//                    this.clustering = moduleManager.getModule("ApolloSimpleFireSearch.Clustering.Police", "adf.sample.module.algorithm.SampleKMeans");
                    this.clustering = moduleManager.getModule("RoadDetector.Clustering.Police", "adf.sample.module.algorithm.SampleKMeans");
                }
                break;
        }

        this.world = ApolloWorld.load(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);

        humanSearchMethod = new HumanSearchMethod(world, ai, wi);
        simpleSearchMethod = new SimpleSearchMethod(world, ai, wi);
        searchStrategy = new HumanSearchStrategy(world, pathPlanning, wi, ai);

    }

    @Override
    public Search precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        if (getCountPrecompute() >= 2) {
            return this;
        }
        this.world.precompute(precomputeData);
        this.pathPlanning.precompute(precomputeData);
        this.clustering.precompute(precomputeData);
        humanSearchMethod.initialize();
        simpleSearchMethod.initialize();
        return this;
    }

    @Override
    public Search resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if (getCountResume() >= 2) {
            return this;
        }
        this.world.resume(precomputeData);
        this.pathPlanning.resume(precomputeData);
        this.clustering.resume(precomputeData);
        humanSearchMethod.initialize();
        simpleSearchMethod.initialize();
        return this;
    }

    @Override
    public Search preparate() {
        super.preparate();
        if (getCountPreparate() >= 2) {
            return this;
        }
        this.world.preparate();
        this.pathPlanning.preparate();
        this.clustering.preparate();
        humanSearchMethod.initialize();
        simpleSearchMethod.initialize();
        return this;
    }

    private List<Integer> findElements(Collection<StandardEntity> elements) {
        if (elements != null) {
            return elements.stream().map(entity -> entity.getID().getValue()).collect(Collectors.toList());
        } else return null;
    }

    @Override
    public Search updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        if (getCountUpdateInfo() >= 2) {
            return this;
        }

        this.world.updateInfo(messageManager);
        this.pathPlanning.updateInfo(messageManager);
        this.clustering.updateInfo(messageManager);


        int clusterIndex = clustering.getClusterIndex(this.agentInfo.getID());
        Collection<StandardEntity> elements = clustering.getClusterEntities(clusterIndex);

//        if (ApolloPersonalData.DEBUG_MODE) {
//            List<Integer> elementList = findElements(elements);
//            //todo send data to Buildings layer
//            VDClient.getInstance().drawAsync(agentInfo.getID().getValue(), "MrlSampleBuildingsLayer", (Serializable) elementList);
//        }


        lastUpdateTime = world.getTime();
        humanSearchMethod.update();
        return this;
    }

    @Override
    public EntityID getTarget() {
        return target;
    }

    private Path targetPath;

    int simpleDMUpdateTime;

    @Override
    public Search calc() {
        if (agentInfo.getTime() < scenarioInfo.getKernelAgentsIgnoreuntil()) {
            return this;
        }
        execute();
        if (getTarget() == null) {


            EntityID targetTemp;
            for (int i = 0; i < 10; i++) {
                targetTemp = getSimpleSearchTarget();
                if (targetTemp != null) {
                    this.target = targetTemp;
                    break;
                }
            }

        } else {
            targetPath = null;
        }

//        if (target==null){
//
//        }
        return this;
    }

    private EntityID getSimpleSearchTarget() {
        if (simpleDMUpdateTime < agentInfo.getTime()) {
            simpleDMUpdateTime = agentInfo.getTime();
            simpleSearchMethod.update();
        }

        if (targetPath == null) {
            targetPath = simpleSearchMethod.getNextPath();
        }
        searchStrategy.setSearchingPath(targetPath, true);
        ActionMove action = (ActionMove) searchStrategy.searchPath();
        if (action != null) {
            if (!action.getUsePosition()) {
                return (action.getPath() != null && !action.getPath().isEmpty() ? action.getPath().get(action.getPath().size() - 1) : null);
            }
        }
        targetPath = null;
        return null;
    }


    private boolean isNeedToEvaluatePath() {
        boolean need = false;
        if (targetPath == null) {
            need = true;
        }
//        if (isPartitionChanged()) {
//            need = true;
//        }
        return need;
    }


    private void execute() {

        logger.debug("Execute.");

        //this condition useful for prevent in cycle loop....
        if (world.getTime() == lastExecuteTime) {
            thisCycleExecute++;
        } else {
            lastExecuteTime = world.getTime();
            thisCycleExecute = 0;
        }
        if (thisCycleExecute > 10) {

            if (ApolloConstants.DEBUG_SEARCH) {
                world.printData("This cycle had too much execute... search failed!");
            }
            return;
        }

        if (isNeedToChangeTarget()) {
            if (isNeedUpdateDecisionMaker()) {
                lastUpdateTime = world.getTime();
                humanSearchMethod.update();
            }
            needChange = false;
            targetArea = humanSearchMethod.getNextArea();
            if (targetArea == null) {
                target = null;
                if (ApolloConstants.DEBUG_SEARCH) {
                    world.printData("targetArea is set to: " + null);
                }
                return;
            } else {
                target = targetArea.getID();
            }
            if (ApolloConstants.DEBUG_SEARCH) {
                world.printData("targetArea is set to: " + targetArea);
            }
        } else if (!greedyStrategy /*&& !humanSearchMethod.searchInPartition*/) {
            if (isNeedUpdateDecisionMaker()) {
                lastUpdateTime = world.getTime();
                humanSearchMethod.update();
            }
            Area betterTarget = humanSearchMethod.getBetterTarget(targetArea);
            if (ApolloConstants.DEBUG_SEARCH) {
                world.printData("betterTarget is: " + targetArea);
            }
            if (betterTarget != null) {
                targetArea = betterTarget;
            }

            if (targetArea != null) {
                target = targetArea.getID();
            } else {
                target = null;
            }
        }

//        if (targetArea != null) {
//            humanSearchMethod.setBuildingInProgress(targetArea.getID());
//        } else {
//            humanSearchMethod.setBuildingInProgress(null);
//        }
//        if (allowMove) {
        target = null;
        ActionMove action = (ActionMove) searchStrategy.searchBuilding((Building) targetArea);
        if (action != null) {
            if (!action.getUsePosition()) {
                target = (!action.getPath().isEmpty() ? action.getPath().get(action.getPath().size() - 1) : null);
            }
        }
        if (target == null) {
            needChange = true;
            execute();
        }
//        }
    }

    /**
     * this method check need to change target or not
     *
     * @return return true if should change target.
     */
    private boolean isNeedToChangeTarget() {
        boolean need = false;
        if (targetArea == null || world.getBuildingModel(targetArea.getID()).isVisited() || needChange) {
            need = true;
        }
        return need;
    }

    /**
     * check need for update decision maker or not!
     *
     * @return return true if need to update
     */
    private boolean isNeedUpdateDecisionMaker() {
        boolean need = false;
        if (lastUpdateTime < world.getTime() || needChange) {
            need = true;
        }
        return need;
    }

}
