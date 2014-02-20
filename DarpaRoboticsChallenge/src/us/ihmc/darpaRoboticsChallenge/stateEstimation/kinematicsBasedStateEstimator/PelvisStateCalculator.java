package us.ihmc.darpaRoboticsChallenge.stateEstimation.kinematicsBasedStateEstimator;


import static com.yobotics.simulationconstructionset.util.math.filter.AlphaFilteredYoVariable.computeAlphaGivenBreakFrequencyProperly;

import java.util.Collection;

import javax.vecmath.Vector3d;

import us.ihmc.commonWalkingControlModules.bipedSupportPolygons.ContactablePlaneBody;
import us.ihmc.controlFlow.ControlFlowOutputPort;
import us.ihmc.darpaRoboticsChallenge.DRCConfigParameters;
import us.ihmc.darpaRoboticsChallenge.sensors.WrenchBasedFootSwitch;
import us.ihmc.robotSide.RobotSide;
import us.ihmc.robotSide.SideDependentList;
import us.ihmc.sensorProcessing.stateEstimation.JointAndIMUSensorDataSource;
import us.ihmc.sensorProcessing.stateEstimation.evaluation.FullInverseDynamicsStructure;
import us.ihmc.utilities.math.MathTools;
import us.ihmc.utilities.math.geometry.FramePoint;
import us.ihmc.utilities.math.geometry.FrameVector;
import us.ihmc.utilities.math.geometry.ReferenceFrame;
import us.ihmc.utilities.screwTheory.CenterOfMassCalculator;
import us.ihmc.utilities.screwTheory.CenterOfMassJacobian;
import us.ihmc.utilities.screwTheory.RigidBody;
import us.ihmc.utilities.screwTheory.ScrewTools;
import us.ihmc.utilities.screwTheory.TwistCalculator;
import us.ihmc.utilities.screwTheory.Wrench;

import com.yobotics.simulationconstructionset.BooleanYoVariable;
import com.yobotics.simulationconstructionset.DoubleYoVariable;
import com.yobotics.simulationconstructionset.EnumYoVariable;
import com.yobotics.simulationconstructionset.VariableChangedListener;
import com.yobotics.simulationconstructionset.YoVariable;
import com.yobotics.simulationconstructionset.YoVariableRegistry;
import com.yobotics.simulationconstructionset.util.graphics.DynamicGraphicObjectsListRegistry;
import com.yobotics.simulationconstructionset.util.math.filter.AlphaFilteredYoVariable;
import com.yobotics.simulationconstructionset.util.math.filter.GlitchFilteredBooleanYoVariable;
import com.yobotics.simulationconstructionset.util.math.frames.YoFramePoint;
import com.yobotics.simulationconstructionset.util.math.frames.YoFrameVector;
import com.yobotics.simulationconstructionset.util.statemachines.State;
import com.yobotics.simulationconstructionset.util.statemachines.StateMachine;
import com.yobotics.simulationconstructionset.util.statemachines.StateMachineTools;

public class PelvisStateCalculator
{
   private final YoVariableRegistry registry = new YoVariableRegistry(getClass().getSimpleName());
   
   private static final ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();

   private final YoFrameVector imuAccelerationInWorld = new YoFrameVector("imuAccelerationInWorld", worldFrame, registry);
   private final YoFrameVector pelvisVelocityByIntegrating = new YoFrameVector("pelvisVelocityByIntegrating", worldFrame, registry);  

   private final CenterOfMassCalculator centerOfMassCalculator;
   private final CenterOfMassJacobian centerOfMassJacobianBody;

   private final YoFramePoint pelvisPosition = new YoFramePoint("estimatedPelvisPosition", worldFrame, registry);
   private final YoFrameVector pelvisVelocity = new YoFrameVector("estimatedPelvisVelocity", worldFrame, registry);
   
   private final YoFramePoint centerOfMassPosition = new YoFramePoint("estimatedCenterOfMassPosition", worldFrame, registry);
   private final YoFrameVector centerOfMassVelocity = new YoFrameVector("estimatedCenterOfMassVelocity", worldFrame, registry);

   private final DoubleYoVariable alphaPelvisAccelerometerIntegrationToVelocity = new DoubleYoVariable("alphaPelvisAccelerometerIntegrationToVelocity", registry);
   private final DoubleYoVariable alphaPelvisAccelerometerIntegrationToPosition = new DoubleYoVariable("alphaPelvisAccelerometerIntegrationToPosition", registry);
   
   private final DoubleYoVariable feetSpacing = new DoubleYoVariable("estimatedFeetSpacing", registry);
   private final DoubleYoVariable alphaFeetSpacingVelocity = new DoubleYoVariable("alphaFeetSpacingVelocity", registry);
   private final AlphaFilteredYoVariable feetSpacingVelocity = new AlphaFilteredYoVariable("estimatedFeetSpacingVelocity", registry, alphaFeetSpacingVelocity);
   private final DoubleYoVariable footVelocityThreshold = new DoubleYoVariable("footVelocityThresholdToFilterTrustedFoot", registry);
   
   private final SideDependentList<DoubleYoVariable> footForcesZInPercentOfTotalForce = new SideDependentList<DoubleYoVariable>();
   private final DoubleYoVariable forceZInPercentThresholdToFilterFoot = new DoubleYoVariable("forceZInPercentThresholdToFilterFootUserParameter", registry);

   private final SideDependentList<WrenchBasedFootSwitch> footSwitches;
   private final SideDependentList<Wrench> footWrenches = new SideDependentList<Wrench>(new Wrench(), new Wrench());
   private final DoubleYoVariable delayTimeBeforeTrustingFoot = new DoubleYoVariable("delayTimeBeforeTrustingFoot", registry);
   private final SideDependentList<GlitchFilteredBooleanYoVariable> haveFeetHitGroundFiltered = new SideDependentList<GlitchFilteredBooleanYoVariable>();
   private final SideDependentList<BooleanYoVariable> areFeetTrusted = new SideDependentList<BooleanYoVariable>();
   
   private final ReferenceFrame pelvisFrame;
   private final SideDependentList<ReferenceFrame> footFrames;
   
   private final RigidBody pelvis;
   
   private ControlFlowOutputPort<Vector3d> linearAccelerationPort;

   private final double estimatorDT;
      
   private final DoubleYoVariable alphaGravityEstimation = new DoubleYoVariable("alphaGravityEstimation", registry);
   private final AlphaFilteredYoVariable gravityEstimation = new AlphaFilteredYoVariable("gravityEstimation", registry, alphaGravityEstimation);

   private final SideDependentList<ContactablePlaneBody> bipedFeet;
   
   // temporary variables
   private final FrameVector tempFrameVector = new FrameVector();
   private final FramePoint tempPosition = new FramePoint();
   private final FrameVector tempVelocity = new FrameVector();

   private final BooleanYoVariable reinitialize = new BooleanYoVariable("reinitialize", registry);

   private enum SlippageCompensatorMode {LOAD_THRESHOLD, MIN_PELVIS_ACCEL};
   private final EnumYoVariable<SlippageCompensatorMode> slippageCompensatorMode = new EnumYoVariable<SlippageCompensatorMode>("slippageCompensatorMode", registry, SlippageCompensatorMode.class);
   
   private enum PelvisEstimationState {TRUST_BOTH_FEET, TRUST_LEFT_FOOT, TRUST_RIGHT_FOOT};

   private final SideDependentList<PelvisEstimationState> robotSideToPelvisEstimationState = new SideDependentList<PelvisStateCalculator.PelvisEstimationState>(
         PelvisEstimationState.TRUST_LEFT_FOOT, PelvisEstimationState.TRUST_RIGHT_FOOT);
   
   private final DoubleYoVariable yoTime = new DoubleYoVariable("t_pelvisStateEstimator", registry);
   
   private final EnumYoVariable<PelvisEstimationState> requestedState = new EnumYoVariable<>("requestedPelvisEstimationState", "", registry, PelvisEstimationState.class, true);
   
   private final StateMachine<PelvisEstimationState> stateMachine;

   private final TwistCalculator twistCalculator;
   
   private final PelvisKinematicsBasedPositionCalculator pelvisKinematicsBasedPositionCalculator;
   private final IMUDriftCompensator imuDriftCompensator;
   
   // Temporary variables
   private final FrameVector tempIMUAcceleration = new FrameVector();
   private final FrameVector tempPelvisVelocityIntegrated = new FrameVector();
   private final FrameVector tempEstimatedVelocityIMUPart = new FrameVector();
   private final FramePoint tempEstimatedPositionIMUPart = new FramePoint();
   private final FramePoint tempCenterOfMassPositionWorld = new FramePoint(worldFrame);
   private final FrameVector tempCenterOfMassVelocityWorld = new FrameVector(worldFrame);
   private final SideDependentList<Double> accelerationMagnitudeErrors = new SideDependentList<Double>(0.0, 0.0);
   
   public PelvisStateCalculator(FullInverseDynamicsStructure inverseDynamicsStructure, SideDependentList<WrenchBasedFootSwitch> footSwitches,
         SideDependentList<ContactablePlaneBody> bipedFeet, double gravitationalAcceleration, final double estimatorDT,
         DynamicGraphicObjectsListRegistry dynamicGraphicObjectsListRegistry, YoVariableRegistry parentRegistry)
   {      
      this.estimatorDT = estimatorDT;
      this.footSwitches = footSwitches;
      this.bipedFeet = bipedFeet;
      twistCalculator = inverseDynamicsStructure.getTwistCalculator();
      pelvisFrame = inverseDynamicsStructure.getRootJoint().getFrameAfterJoint();
      
      pelvis = inverseDynamicsStructure.getRootJoint().getSuccessor();

      
      gravityEstimation.reset();
      gravityEstimation.update(Math.abs(gravitationalAcceleration));
      
      footFrames = new SideDependentList<ReferenceFrame>();
      
      RigidBody elevator = inverseDynamicsStructure.getElevator();
      this.centerOfMassCalculator = new CenterOfMassCalculator(elevator, pelvisFrame);
      this.centerOfMassJacobianBody = new CenterOfMassJacobian(ScrewTools.computeSupportAndSubtreeSuccessors(elevator),
              ScrewTools.computeSubtreeJoints(pelvis), pelvisFrame);

      setupBunchOfVariables();
      
      forceZInPercentThresholdToFilterFoot.addVariableChangedListener(new VariableChangedListener()
      {
         @Override
         public void variableChanged(YoVariable v)
         {
            double valueClipped = MathTools.clipToMinMax(forceZInPercentThresholdToFilterFoot.getDoubleValue(), 0.0, 0.45);
            forceZInPercentThresholdToFilterFoot.set(valueClipped);
         }
      });
      
      reinitialize.addVariableChangedListener(new VariableChangedListener()
      {
         @Override
         public void variableChanged(YoVariable v)
         {
            if (reinitialize.getBooleanValue())
               initialize();
         }
      });

      stateMachine = new StateMachine<PelvisEstimationState>("PelvisEstimationStateMachine", "switchTime", PelvisEstimationState.class, yoTime, registry);
      setupStateMachine();
      
      pelvisKinematicsBasedPositionCalculator = new PelvisKinematicsBasedPositionCalculator(twistCalculator, pelvis, pelvisFrame, bipedFeet, estimatorDT, dynamicGraphicObjectsListRegistry, registry);
      pelvisKinematicsBasedPositionCalculator.setAlphaPelvisPosition(0.0);
      pelvisKinematicsBasedPositionCalculator.setAlphaPelvisLinearVelocity(computeAlphaGivenBreakFrequencyProperly(16.0, estimatorDT));
      pelvisKinematicsBasedPositionCalculator.setPelvisLinearVelocityBacklashParameters(computeAlphaGivenBreakFrequencyProperly(16.0, estimatorDT),
            DRCConfigParameters.JOINT_VELOCITY_SLOP_TIME_FOR_BACKLASH_COMPENSATION);
      pelvisKinematicsBasedPositionCalculator.setAlphaCenterOfPressure(computeAlphaGivenBreakFrequencyProperly(4.0, estimatorDT));

      alphaGravityEstimation.set(computeAlphaGivenBreakFrequencyProperly(5.3052e-4, estimatorDT)); // alpha = 0.99999 with dt = 0.003

      alphaPelvisAccelerometerIntegrationToVelocity.set(computeAlphaGivenBreakFrequencyProperly(0.4261, estimatorDT)); // alpha = 0.992 with dt = 0.003
      alphaPelvisAccelerometerIntegrationToPosition.set(computeAlphaGivenBreakFrequencyProperly(11.7893, estimatorDT)); // alpha = 0.8 with dt = 0.003

      delayTimeBeforeTrustingFoot.set(0.02);

      footVelocityThreshold.set(Double.MAX_VALUE); // 0.01);

      forceZInPercentThresholdToFilterFoot.set(0.3); 
      
      slippageCompensatorMode.set(SlippageCompensatorMode.LOAD_THRESHOLD);

      imuDriftCompensator = new IMUDriftCompensator(footFrames, pelvisFrame, twistCalculator, inverseDynamicsStructure.getRootJoint(), estimatorDT, registry);
      imuDriftCompensator.activateEstimation(false);
      imuDriftCompensator.activateCompensation(false);
      imuDriftCompensator.setAlphaIMUDrift(computeAlphaGivenBreakFrequencyProperly(0.5332, estimatorDT));
      imuDriftCompensator.setAlphaFootAngularVelocity(computeAlphaGivenBreakFrequencyProperly(0.5332, estimatorDT));
      imuDriftCompensator.setFootAngularVelocityThreshold(0.03);
      
      parentRegistry.addChild(registry);
   }

   private void setupBunchOfVariables()
   {
      int windowSize = (int)(delayTimeBeforeTrustingFoot.getDoubleValue() / estimatorDT);
      
      for (RobotSide robotSide : RobotSide.values)
      {
         ReferenceFrame footFrame = bipedFeet.get(robotSide).getPlaneFrame();
         footFrames.put(robotSide, footFrame);
         
         String sidePrefix = robotSide.getCamelCaseNameForStartOfExpression();
         
         final GlitchFilteredBooleanYoVariable hasFootHitTheGroundFiltered = new GlitchFilteredBooleanYoVariable("has" + robotSide.getCamelCaseNameForMiddleOfExpression() + "FootHitGroundFiltered", registry, windowSize);
         hasFootHitTheGroundFiltered.set(true);
         haveFeetHitGroundFiltered.put(robotSide, hasFootHitTheGroundFiltered);
         
         BooleanYoVariable isFootTrusted = new BooleanYoVariable("is" + robotSide.getCamelCaseNameForMiddleOfExpression() + "FootTrusted", registry);
         isFootTrusted.set(true);
         areFeetTrusted.put(robotSide, isFootTrusted);
         
         DoubleYoVariable footForceZInPercentOfTotalForce = new DoubleYoVariable(sidePrefix + "FootForceZInPercentOfTotalForce", registry);
         footForcesZInPercentOfTotalForce.put(robotSide, footForceZInPercentOfTotalForce);
         
         delayTimeBeforeTrustingFoot.addVariableChangedListener(new VariableChangedListener()
         {
            public void variableChanged(YoVariable v)
            {
               int windowSize = (int)(delayTimeBeforeTrustingFoot.getDoubleValue() / estimatorDT);
               hasFootHitTheGroundFiltered.setWindowSize(windowSize);
            }
         });
      }
   }

   public void startIMUDriftEstimation()
   {
      imuDriftCompensator.activateEstimation(true);
   }
   
   public void startIMUDriftCompensation()
   {
      imuDriftCompensator.activateCompensation(true);
   }
   
   public void initialize()
   {
      initializeRobotState();
   }

   public void initializeRobotState()
   {
      reinitialize.set(false);
      
      imuDriftCompensator.initialize();
      
      centerOfMassCalculator.compute();
      centerOfMassCalculator.packCenterOfMass(tempPosition);
      tempFrameVector.setAndChangeFrame(tempPosition);
      tempFrameVector.changeFrame(worldFrame);

      pelvisPosition.set(centerOfMassPosition);
      pelvisPosition.sub(tempFrameVector);
      pelvisVelocity.setToZero();

      pelvisPosition.getFramePointAndChangeFrameOfPackedPoint(tempPosition);
      pelvisKinematicsBasedPositionCalculator.initialize(tempPosition);
   }

   public void initializeCoMPositionToActual(FramePoint estimatedCoMPosition)
   {
      centerOfMassPosition.set(estimatedCoMPosition);
   }
   
   @SuppressWarnings("unchecked")
   private void setupStateMachine()
   {
      TrustBothFeetState trustBothFeetState = new TrustBothFeetState();
      TrustOneFootState trustLeftFootState = new TrustOneFootState(RobotSide.LEFT);
      TrustOneFootState trustRightFootState = new TrustOneFootState(RobotSide.RIGHT);

      StateMachineTools.addRequestedStateTransition(requestedState, false, trustBothFeetState, trustLeftFootState);
      StateMachineTools.addRequestedStateTransition(requestedState, false, trustBothFeetState, trustRightFootState);

      StateMachineTools.addRequestedStateTransition(requestedState, false, trustLeftFootState, trustBothFeetState);
      StateMachineTools.addRequestedStateTransition(requestedState, false, trustRightFootState, trustBothFeetState);

      StateMachineTools.addRequestedStateTransition(requestedState, false, trustLeftFootState, trustRightFootState);
      StateMachineTools.addRequestedStateTransition(requestedState, false, trustRightFootState, trustLeftFootState);
      
      stateMachine.addState(trustBothFeetState);
      stateMachine.addState(trustLeftFootState);
      stateMachine.addState(trustRightFootState);
   }
   
   private void defaultActionIntoStates()
   {
      yoTime.add(estimatorDT); //Hack to have a yoTime for the state machine
      
      imuDriftCompensator.updateAndCompensateDrift();
      
      pelvisKinematicsBasedPositionCalculator.updateKinematics();
      
      updatePelvisAccelerationWithIMU();
      
      int numberOfEndEffectorsTrusted = setTrustedFeetUsingFootSwitches();

      computeFeetSpacingLengthAndVelocity();

      if (numberOfEndEffectorsTrusted >= 2)
      {
         switch(slippageCompensatorMode.getEnumValue())
         {
         case LOAD_THRESHOLD:
            numberOfEndEffectorsTrusted = filterTrustedFeetBasedOnContactForces();
            break;
         case MIN_PELVIS_ACCEL:
            numberOfEndEffectorsTrusted = filterTrustedFeetBasedOnMinPelvisAcceleration();
            break;
         default:
            throw new RuntimeException("Should not get there");
         }
      }
      
      if (numberOfEndEffectorsTrusted == 2)
         requestedState.set(PelvisEstimationState.TRUST_BOTH_FEET);
      else if (areFeetTrusted.get(RobotSide.LEFT).getBooleanValue())
         requestedState.set(PelvisEstimationState.TRUST_LEFT_FOOT);
      else
         requestedState.set(PelvisEstimationState.TRUST_RIGHT_FOOT);
      
      if (stateMachine.getCurrentStateEnum() == requestedState.getEnumValue())
         requestedState.set(null);

      if (numberOfEndEffectorsTrusted == 0)
         throw new RuntimeException("No foot trusted!");
}

   private void updatePelvisAccelerationWithIMU()
   {
      if (linearAccelerationPort == null)
         return;
      
      tempIMUAcceleration.set(pelvisFrame, linearAccelerationPort.getData());
      gravityEstimation.update(tempIMUAcceleration.length());
      
      tempIMUAcceleration.changeFrame(worldFrame);
      tempIMUAcceleration.setZ(tempIMUAcceleration.getZ() - gravityEstimation.getDoubleValue());
      imuAccelerationInWorld.set(tempIMUAcceleration);
   }

   private int setTrustedFeetUsingFootSwitches()
   {
      int numberOfEndEffectorsTrusted = 0;
      
      for(RobotSide robotSide : RobotSide.values)
      {
         if (footSwitches.get(robotSide).hasFootHitGround())
            haveFeetHitGroundFiltered.get(robotSide).update(true);
         else
            haveFeetHitGroundFiltered.get(robotSide).set(false);
         
         if (haveFeetHitGroundFiltered.get(robotSide).getBooleanValue())
            numberOfEndEffectorsTrusted++;
      }
      
      // Update only if at least one foot hit the ground
      if (numberOfEndEffectorsTrusted > 0)
      {
         for(RobotSide robotSide : RobotSide.values)
            areFeetTrusted.get(robotSide).set(haveFeetHitGroundFiltered.get(robotSide).getBooleanValue());
      }
      // Else keep the old states
      else
      {
         numberOfEndEffectorsTrusted = 0;
         for(RobotSide robotSide : RobotSide.values)
         {
            if (areFeetTrusted.get(robotSide).getBooleanValue())
               numberOfEndEffectorsTrusted++;
         }
      }
      
      return numberOfEndEffectorsTrusted;
   }
   
   private int filterTrustedFeetBasedOnContactForces()
   {
      int numberOfEndEffectorsTrusted = 2;
      
      double totalForceZ = 0.0;
      for (RobotSide robotSide : RobotSide.values)
      {
         Wrench footWrench = footWrenches.get(robotSide);
         footSwitches.get(robotSide).computeAndPackFootWrench(footWrench);
         totalForceZ += footWrench.getLinearPartZ();
      }
      
      for (RobotSide robotSide : RobotSide.values)
      {
         Wrench footWrench = footWrenches.get(robotSide);
         footForcesZInPercentOfTotalForce.get(robotSide).set(footWrench.getLinearPartZ() / totalForceZ);
         
         if (footForcesZInPercentOfTotalForce.get(robotSide).getDoubleValue() < forceZInPercentThresholdToFilterFoot.getDoubleValue())
         {
            numberOfEndEffectorsTrusted--;
            areFeetTrusted.get(robotSide).set(false);
            
            return numberOfEndEffectorsTrusted;
         }
      }
      
      return numberOfEndEffectorsTrusted;
   }

   private int filterTrustedFeetBasedOnMinPelvisAcceleration()
   {
      int numberOfEndEffectorsTrusted = 2;
      
      if (feetSpacingVelocity.getDoubleValue() < footVelocityThreshold.getDoubleValue())
         return numberOfEndEffectorsTrusted;
      
      for (RobotSide robotSide : RobotSide.values)
      {
         imuAccelerationInWorld.getFrameVector(tempIMUAcceleration);
         pelvisKinematicsBasedPositionCalculator.getFootToPelvisAcceleration(tempFrameVector, robotSide);
         tempFrameVector.sub(tempIMUAcceleration);
         accelerationMagnitudeErrors.put(robotSide, tempFrameVector.length());
      }
      
      for (RobotSide robotSide : RobotSide.values)
      {
         if (accelerationMagnitudeErrors.get(robotSide) > accelerationMagnitudeErrors.get(robotSide.getOppositeSide()))
         {
            areFeetTrusted.get(robotSide).set(false);
            numberOfEndEffectorsTrusted--;
            return numberOfEndEffectorsTrusted;
         }
      }
      
      return numberOfEndEffectorsTrusted;
   }

   private void computeFeetSpacingLengthAndVelocity()
   {
      tempFrameVector.setToZero(worldFrame);
      
      for (RobotSide robotSide : RobotSide.values)
      {
         pelvisKinematicsBasedPositionCalculator.getFootToPelvisPosition(tempPosition, robotSide);
         tempPosition.scale(robotSide.negateIfRightSide(1.0));
         tempFrameVector.add(tempPosition);
      }
      
      feetSpacing.set(tempFrameVector.length());

      tempFrameVector.setToZero(worldFrame);
      
      for (RobotSide robotSide : RobotSide.values)
      {
         pelvisKinematicsBasedPositionCalculator.getFootToPelvisVelocity(tempVelocity, robotSide);
         tempVelocity.scale(robotSide.negateIfRightSide(1.0));
         tempFrameVector.add(tempVelocity);
      }
      feetSpacingVelocity.update(tempFrameVector.length());
   }
   
   private class TrustBothFeetState extends State<PelvisEstimationState>
   {

      public TrustBothFeetState()
      {
         super(PelvisEstimationState.TRUST_BOTH_FEET);
      }

      @Override
      public void doAction()
      {
         pelvisKinematicsBasedPositionCalculator.updatePelvisPositionForDoubleSupport(footSwitches);
         imuDriftCompensator.esimtateDriftIfPossible(true);
      }

      @Override
      public void doTransitionIntoAction()
      {
         requestedState.set(null);
      }

      @Override
      public void doTransitionOutOfAction()
      {
      }
   }

   private class TrustOneFootState extends State<PelvisEstimationState>
   {
      private final RobotSide trustedSide;

      public TrustOneFootState(RobotSide trustedSide)
      {
         super(robotSideToPelvisEstimationState.get(trustedSide));
         this.trustedSide = trustedSide;
      }

      @Override
      public void doAction()
      {
         imuDriftCompensator.esimtateDriftIfPossible(false);
         pelvisPosition.getFramePoint(tempPosition);
         pelvisKinematicsBasedPositionCalculator.updatePelvisPositionForSingleSupport(tempPosition, footSwitches, trustedSide);
      }
      
      @Override
      public void doTransitionIntoAction()
      {
         requestedState.set(null);
      }

      @Override
      public void doTransitionOutOfAction()
      {
      }
   }

   public void run()
   {
      defaultActionIntoStates();
      
      stateMachine.checkTransitionConditions();
      stateMachine.doAction();
      
      defaultActionOutOfStates();
   }
   
   private void defaultActionOutOfStates()
   {
      if (linearAccelerationPort != null)
      {
         computePelvisStateByIntegratingAccelerometerAndMergeWithKinematics();
      
      }   
      else
      {
         pelvisKinematicsBasedPositionCalculator.getPelvisPositionAndVelocity(tempPosition, tempVelocity);
         pelvisPosition.set(tempPosition);
         pelvisVelocity.set(tempVelocity);
      }
      
      updateCoMState();
   }
   
   private void computePelvisStateByIntegratingAccelerometerAndMergeWithKinematics()
   {
      imuAccelerationInWorld.getFrameVector(tempIMUAcceleration);
      tempIMUAcceleration.scale(estimatorDT);
      pelvisVelocityByIntegrating.add(tempIMUAcceleration);
      
      pelvisVelocity.getFrameVectorAndChangeFrameOfPackedVector(tempEstimatedVelocityIMUPart);
      tempEstimatedVelocityIMUPart.add(tempIMUAcceleration);
      tempEstimatedVelocityIMUPart.scale(alphaPelvisAccelerometerIntegrationToVelocity.getDoubleValue());
      
      pelvisKinematicsBasedPositionCalculator.getPelvisVelocity(tempVelocity);
      pelvisVelocity.set(tempVelocity);
      
      pelvisVelocity.scale(1.0 - alphaPelvisAccelerometerIntegrationToVelocity.getDoubleValue());
      pelvisVelocity.add(tempEstimatedVelocityIMUPart);
      
      pelvisVelocity.getFrameVectorAndChangeFrameOfPackedVector(tempPelvisVelocityIntegrated);
      tempPelvisVelocityIntegrated.scale(estimatorDT);
      pelvisPosition.getFramePointAndChangeFrameOfPackedPoint(tempEstimatedPositionIMUPart);
      tempEstimatedPositionIMUPart.add(tempPelvisVelocityIntegrated);
      tempEstimatedPositionIMUPart.scale(alphaPelvisAccelerometerIntegrationToPosition.getDoubleValue());

      pelvisKinematicsBasedPositionCalculator.getPelvisPosition(tempPosition);
      pelvisPosition.set(tempPosition);
      pelvisPosition.scale(1.0 - alphaPelvisAccelerometerIntegrationToPosition.getDoubleValue());
      pelvisPosition.add(tempEstimatedPositionIMUPart);
   }

   private void updateCoMState()
   {
      centerOfMassCalculator.compute();
      centerOfMassCalculator.packCenterOfMass(tempCenterOfMassPositionWorld);
      tempCenterOfMassPositionWorld.changeFrame(worldFrame);
      centerOfMassPosition.set(tempCenterOfMassPositionWorld);

      centerOfMassJacobianBody.compute();
      tempCenterOfMassVelocityWorld.setToZero(pelvisFrame);
      centerOfMassJacobianBody.packCenterOfMassVelocity(tempCenterOfMassVelocityWorld);
      tempCenterOfMassVelocityWorld.changeFrame(worldFrame);
      pelvisVelocity.getFrameVectorAndChangeFrameOfPackedVector(tempFrameVector);
      tempCenterOfMassVelocityWorld.add(tempFrameVector);
      centerOfMassVelocity.set(tempCenterOfMassVelocityWorld);
   }

   public void getEstimatedPelvisPosition(FramePoint pelvisPositionToPack)
   {
      pelvisPosition.getFramePointAndChangeFrameOfPackedPoint(pelvisPositionToPack);
   }

   public void getEstimatedPelvisLinearVelocity(FrameVector pelvisLinearVelocityToPack)
   {
      pelvisVelocity.getFrameVectorAndChangeFrameOfPackedVector(pelvisLinearVelocityToPack);
   }

   public void getEstimatedCoMPosition(FramePoint comPositionToPack)
   {
      centerOfMassPosition.getFramePointAndChangeFrameOfPackedPoint(comPositionToPack);
   }

   public void getEstimatedCoMVelocity(FrameVector comVelocityToPack)
   {
      centerOfMassVelocity.getFrameVectorAndChangeFrameOfPackedVector(comVelocityToPack);
   }

   public void setJointAndIMUSensorDataSource(JointAndIMUSensorDataSource jointAndIMUSensorDataSource)
   {
      Collection<ControlFlowOutputPort<Vector3d>> linearAccelerationOutputPorts = jointAndIMUSensorDataSource.getSensorMap().getLinearAccelerationOutputPorts();

      for (ControlFlowOutputPort<Vector3d> controlFlowOutputPort : linearAccelerationOutputPorts)
      {
         linearAccelerationPort = controlFlowOutputPort;
      }
   }
}
