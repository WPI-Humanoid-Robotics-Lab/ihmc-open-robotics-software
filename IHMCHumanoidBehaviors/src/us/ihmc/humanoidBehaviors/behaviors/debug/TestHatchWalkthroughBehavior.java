package us.ihmc.humanoidBehaviors.behaviors.debug;

import us.ihmc.communication.packets.PacketDestination;
import us.ihmc.communication.packets.TextToSpeechPacket;
import us.ihmc.euclid.axisAngle.AxisAngle;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.euclid.tuple4D.Quaternion;
import us.ihmc.humanoidBehaviors.behaviors.AbstractBehavior;
import us.ihmc.humanoidBehaviors.behaviors.behaviorServices.BehaviorService;
import us.ihmc.humanoidBehaviors.communication.CommunicationBridgeInterface;
import us.ihmc.humanoidBehaviors.communication.ConcurrentListeningQueue;
import us.ihmc.humanoidRobotics.communication.packets.ExecutionMode;
import us.ihmc.humanoidRobotics.communication.packets.walking.FootstepDataListMessage;
import us.ihmc.humanoidRobotics.communication.packets.walking.FootstepDataMessage;
import us.ihmc.humanoidRobotics.communication.packets.walking.FootstepStatus;
import us.ihmc.humanoidRobotics.communication.packets.walking.FootstepDataMessage.FootstepOrigin;

import us.ihmc.humanoidRobotics.communication.packets.manipulation.ArmTrajectoryMessage;
import us.ihmc.humanoidRobotics.communication.packets.walking.ChestTrajectoryMessage;
import us.ihmc.humanoidRobotics.communication.packets.walking.PelvisOrientationTrajectoryMessage;
import us.ihmc.humanoidRobotics.communication.packets.walking.PelvisTrajectoryMessage;
import us.ihmc.humanoidRobotics.communication.packets.walking.PelvisHeightTrajectoryMessage;
import us.ihmc.humanoidRobotics.communication.packets.walking.FootTrajectoryMessage;
import us.ihmc.humanoidRobotics.communication.packets.wholebody.WholeBodyTrajectoryMessage;

import us.ihmc.humanoidRobotics.frames.HumanoidReferenceFrames;
import us.ihmc.robotics.dataStructures.variable.BooleanYoVariable;
import us.ihmc.robotics.dataStructures.variable.DoubleYoVariable;
import us.ihmc.robotics.geometry.FramePoint;
import us.ihmc.robotics.geometry.FramePose;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.time.YoStopwatch;
import us.ihmc.robotics.trajectories.TrajectoryType;

public class TestHatchWalkthroughBehavior extends AbstractBehavior
{
   private static final ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();
   private final RigidBodyTransform hatchToWorld = new RigidBodyTransform();
   private final ReferenceFrame hatchFrame = ReferenceFrame.constructFrameWithUnchangingTransformFromParent("HatchFrame", worldFrame, hatchToWorld);
   
   private final HumanoidReferenceFrames referenceFrames;
   private final DoubleYoVariable swingTime = new DoubleYoVariable("BehaviorSwingTime", registry);
   private final DoubleYoVariable sleepTime = new DoubleYoVariable("BehaviorSleepTime", registry);
   private final DoubleYoVariable transferTime = new DoubleYoVariable("BehaviorTransferTime", registry);
   private final DoubleYoVariable stepLength = new DoubleYoVariable("BehaviorStepLength", registry);
   private final BooleanYoVariable stepInPlace = new BooleanYoVariable("StepInPlace", registry);
   private final BooleanYoVariable abortBehavior = new BooleanYoVariable("AbortBehavior", registry);

   private final YoStopwatch timer;
   
   double armTrajectoryTime; // was 4.0
   double leftArmGoalPosition[] = new double[] { -1.57, -0.51, 0.0, 2.0, 0.0, 0.0, 0.0 };
   double rightArmGoalPosition[] = new double[] { 1.57, 0.51, 0.0, -2.0, 0.0, 0.0, 0.0 };
   boolean madeFirstStepThroughHatch = false;
   boolean madeSecondStepThroughHatch = false;
   boolean robotConfigurationInitialized = false;
   boolean changedRobotConfiguration = false;
   double counterHelper = 0;
   private final ConcurrentListeningQueue<FootstepStatus> footstepStatusQueue = new ConcurrentListeningQueue<FootstepStatus>(10);

   private int version = 2;

   public TestHatchWalkthroughBehavior(CommunicationBridgeInterface communicationBridge, HumanoidReferenceFrames referenceFrames, DoubleYoVariable yoTime)
   {
      super(communicationBridge);
      this.referenceFrames = referenceFrames;

      swingTime.set(1.2);
      transferTime.set(0.6);
      sleepTime.set(8.0);
      stepLength.set(0.3);

      timer = new YoStopwatch(yoTime);
      
      switch (version)
      {
      case 0:
         armTrajectoryTime = 4.0;
         break;
      case 1:
         armTrajectoryTime = 2.0;
         break;
      case 2:
         armTrajectoryTime = 1.0;
         break;
      default:
         break;
      }
   }
   
   @Override
   public void doControl()
   {
      // Use version from first iteration
//      walkThroughHatchIteration1();
      
      // Use version from second iteration
      walkThroughHatchIteration2();
   }
   
   
   public void walkThroughHatchIteration2()
   {
      if (!robotConfigurationInitialized)
      {
         initializeRobotConfigurationIteration2();
      }

      if (!(timer.totalElapsed() > sleepTime.getDoubleValue()))
      {
         return;
      }
      
      if(!madeFirstStepThroughHatch)
      {  
         makeFirstStepThroughHatchOpeningIteration2();
         counterHelper = timer.totalElapsed();
      }
      else if(!changedRobotConfiguration && (timer.totalElapsed() > (counterHelper + 1.5 * armTrajectoryTime)))
      {
         changeRobotConfigurationIteration2();
         counterHelper = timer.totalElapsed();
      }
      else if(!madeSecondStepThroughHatch && (timer.totalElapsed() > (counterHelper + 1.5 * armTrajectoryTime)))
      {
         makeSecondStepThroughHatchOpeningIteration2();
      }
   }
   
   
   public void initializeRobotConfigurationIteration2()
   {
      AxisAngle chestOrientationAA = new AxisAngle(0.0, 1.0, 0.0, Math.toRadians(10.0));
      Quaternion chestOrientation = new Quaternion(chestOrientationAA);
      ChestTrajectoryMessage chestOrientationTrajectoryMessage = new ChestTrajectoryMessage(armTrajectoryTime, chestOrientation);
      sendPacket(chestOrientationTrajectoryMessage);
      
      AxisAngle pelvisOrientationAA = new AxisAngle(0.0, 1.0, 0.0, Math.toRadians(-15.0));
      Quaternion pelvisOrientation = new Quaternion(pelvisOrientationAA);
      PelvisOrientationTrajectoryMessage pelvisOrientationTrajectoryMessage = new PelvisOrientationTrajectoryMessage(armTrajectoryTime, pelvisOrientation);
      sendPacket(pelvisOrientationTrajectoryMessage);
      
      ReferenceFrame pelvisFrame = referenceFrames.getPelvisFrame();
      FramePose pelvisPose = new FramePose(pelvisFrame);
      pelvisPose.setZ(-0.04);
      pelvisPose.changeFrame(ReferenceFrame.getWorldFrame());
      Point3D pelvisGoalLocation = new Point3D();
      pelvisPose.getPose(pelvisGoalLocation, new Quaternion());

      PelvisHeightTrajectoryMessage pelvisHeightTrajectoryMessage = new PelvisHeightTrajectoryMessage(armTrajectoryTime, pelvisGoalLocation.getZ());
      sendPacket(pelvisHeightTrajectoryMessage);
      
      ArmTrajectoryMessage leftArmTrajectoryMessage = new ArmTrajectoryMessage(RobotSide.LEFT, armTrajectoryTime, leftArmGoalPosition);
      ArmTrajectoryMessage rightArmTrajectoryMessage = new ArmTrajectoryMessage(RobotSide.RIGHT, armTrajectoryTime, rightArmGoalPosition);
      
      sendPacket(leftArmTrajectoryMessage);
      sendPacket(rightArmTrajectoryMessage);

      robotConfigurationInitialized = true;
   }
   
   public void makeFirstStepThroughHatchOpeningIteration2()
   {
      AxisAngle chestOrientationAA = new AxisAngle(0.0, 1.0, 0.0, Math.toRadians(25.0)); // was 10.0
      Quaternion chestOrientation = new Quaternion(chestOrientationAA);
      ChestTrajectoryMessage chestOrientationTrajectoryMessage = new ChestTrajectoryMessage(armTrajectoryTime, chestOrientation);
      sendPacket(chestOrientationTrajectoryMessage);

      AxisAngle pelvisOrientationAA = new AxisAngle(0.0, 1.0, 0.0, Math.toRadians(-5.0)); // was now 0!!! //was 10.0, (might have) worked
      Quaternion pelvisOrientation = new Quaternion(pelvisOrientationAA);

      PelvisOrientationTrajectoryMessage pelvisOrientationTrajectoryMessage = new PelvisOrientationTrajectoryMessage(4.5*armTrajectoryTime, pelvisOrientation); // was 1.5
      sendPacket(pelvisOrientationTrajectoryMessage);
      
      FootstepDataListMessage footsteps = new FootstepDataListMessage(swingTime.getDoubleValue(), transferTime.getDoubleValue());
      footsteps.setExecutionMode(ExecutionMode.OVERRIDE);
      footsteps.setDestination(PacketDestination.BROADCAST);

      ReferenceFrame rightSoleFrame = referenceFrames.getSoleFrame(RobotSide.RIGHT);
      FramePose stepPose = new FramePose(rightSoleFrame);
      stepPose.setX(0.58);
      stepPose.setY(0.08);

      stepPose.changeFrame(ReferenceFrame.getWorldFrame());

      Point3D location = new Point3D();
      Quaternion orientation = new Quaternion();
      stepPose.getPose(location, orientation);

      FootstepDataMessage footstepData = new FootstepDataMessage(RobotSide.RIGHT, location, orientation);
      footstepData.setOrigin(FootstepOrigin.AT_SOLE_FRAME);
      footsteps.add(footstepData);
      
      footstepData.setSwingHeight(0.17);

      sendPacket(footsteps);
      madeFirstStepThroughHatch = true;
   }
   
   public void changeRobotConfigurationIteration2()
   {
      AxisAngle pelvisGoalOrientationYAA = new AxisAngle(0.0, 1.0, 0.0, Math.toRadians(10.0));
      Quaternion pelvisGoalOrientationY = new Quaternion(pelvisGoalOrientationYAA);
      AxisAngle pelvisGoalOrientationXAA = new AxisAngle(1.0, 0.0, 0.0, Math.toRadians(0.0));
      Quaternion pelvisGoalOrientationX = new Quaternion(pelvisGoalOrientationXAA);
      
      Quaternion pelvisGoalOrientation = new Quaternion();
      pelvisGoalOrientation.multiply(pelvisGoalOrientationX, pelvisGoalOrientationY);
      
      ReferenceFrame pelvisFrame = referenceFrames.getPelvisFrame();
      FramePose pelvisPose = new FramePose(pelvisFrame);
      pelvisPose.setX(0.15); // was 0.12 before test
      pelvisPose.setZ(-7.0); // Added as test! 5

      pelvisPose.changeFrame(ReferenceFrame.getWorldFrame());
      Point3D pelvisGoalLocation = new Point3D();
      
      pelvisPose.getPose(pelvisGoalLocation, new Quaternion());

      PelvisTrajectoryMessage pelvisTrajectoryMessage = new PelvisTrajectoryMessage(armTrajectoryTime, pelvisGoalLocation, pelvisGoalOrientation);
      sendPacket(pelvisTrajectoryMessage);
      
      changedRobotConfiguration = true;
   }
   
   public void makeSecondStepThroughHatchOpeningIteration2()
   {
      AxisAngle chestOrientationAA = new AxisAngle(0.0, 1.0, 0.0, Math.toRadians(7.0)); // was 10.0
      Quaternion chestOrientation = new Quaternion(chestOrientationAA);
      ChestTrajectoryMessage chestOrientationTrajectoryMessage = new ChestTrajectoryMessage(2.0, chestOrientation);
      sendPacket(chestOrientationTrajectoryMessage);
      
      ReferenceFrame pelvisFrame = referenceFrames.getPelvisFrame();
      FramePose pelvisPose = new FramePose(pelvisFrame);
      pelvisPose.setZ(0.03);
      pelvisPose.changeFrame(ReferenceFrame.getWorldFrame());
      Point3D pelvisGoalLocation = new Point3D();
      pelvisPose.getPose(pelvisGoalLocation, new Quaternion());
      PelvisHeightTrajectoryMessage pelvisHeightTrajectoryMessage = new PelvisHeightTrajectoryMessage(armTrajectoryTime, pelvisGoalLocation.getZ());
      sendPacket(pelvisHeightTrajectoryMessage);
   
      
      AxisAngle pelvisGoalOrientationYAA = new AxisAngle(0.0, 1.0, 0.0, Math.toRadians(0.0));
      Quaternion pelvisGoalOrientationY = new Quaternion(pelvisGoalOrientationYAA);
      AxisAngle pelvisGoalOrientationXAA = new AxisAngle(1.0, 0.0, 0.0, Math.toRadians(3.0));
      Quaternion pelvisGoalOrientationX = new Quaternion(pelvisGoalOrientationXAA);
      
      Quaternion pelvisGoalOrientation = new Quaternion();
      pelvisGoalOrientation.multiply(pelvisGoalOrientationX, pelvisGoalOrientationY);
      PelvisOrientationTrajectoryMessage pelvisOrientationTrajectoryMessage = new PelvisOrientationTrajectoryMessage(armTrajectoryTime, pelvisGoalOrientation);
      sendPacket(pelvisOrientationTrajectoryMessage);
      
      
      FootstepDataListMessage footsteps = new FootstepDataListMessage(swingTime.getDoubleValue(), transferTime.getDoubleValue());
      footsteps.setExecutionMode(ExecutionMode.OVERRIDE);
      footsteps.setDestination(PacketDestination.BROADCAST);

      ReferenceFrame leftSoleFrame = referenceFrames.getSoleFrame(RobotSide.LEFT);
      FramePose stepPose = new FramePose(leftSoleFrame);
      stepPose.setX(0.58);
      stepPose.setY(0.00);

      stepPose.changeFrame(ReferenceFrame.getWorldFrame());

      Point3D location = new Point3D();
      Quaternion orientation = new Quaternion();
      stepPose.getPose(location, orientation);

      FootstepDataMessage footstepData = new FootstepDataMessage(RobotSide.LEFT, location, orientation);
      footstepData.setOrigin(FootstepOrigin.AT_SOLE_FRAME);
      
      
      FramePose wayPointPose1 = new FramePose(leftSoleFrame);
      wayPointPose1.setX(0.06);
      wayPointPose1.setY(-0.06);
      wayPointPose1.setZ(0.25);
      wayPointPose1.changeFrame(ReferenceFrame.getWorldFrame());
      Point3D locationWayPoint1 = new Point3D();
      wayPointPose1.getPose(locationWayPoint1, new Quaternion());
      
      FramePose wayPointPose2 = new FramePose(leftSoleFrame);
      wayPointPose2.setX(0.45);
      wayPointPose2.setY(0.00);
      wayPointPose2.setZ(0.19);
      wayPointPose2.changeFrame(ReferenceFrame.getWorldFrame());
      Point3D locationWayPoint2 = new Point3D();
      wayPointPose2.getPose(locationWayPoint2, new Quaternion());
      
      footstepData.setTrajectoryType(TrajectoryType.CUSTOM);
      footstepData.setTrajectoryWaypoints(new Point3D[] {locationWayPoint1, locationWayPoint2});
      footsteps.add(footstepData);

      sendPacket(footsteps);
      madeSecondStepThroughHatch = true;
   }
   
   
   
   public void walkThroughHatchIteration1()
   {
      if (!robotConfigurationInitialized)
      {
         initializeRobotConfiguration();
      }

      if (!(timer.totalElapsed() > sleepTime.getDoubleValue()))
      {
         return;
      }
      
      if(!madeFirstStepThroughHatch)
      {  
         makeFirstStepThroughHatchOpening();
         counterHelper = timer.totalElapsed();
      }
      else if(!changedRobotConfiguration && (timer.totalElapsed() > (counterHelper + 1.5 * armTrajectoryTime)))
      {
         changeRobotConfiguration();
         counterHelper = timer.totalElapsed();
      }
      else if(!madeSecondStepThroughHatch && (timer.totalElapsed() > (counterHelper + 1.5 * armTrajectoryTime)))
      {
         makeSecondStepThroughHatchOpening();
      }
   }
   
   public void initializeRobotConfiguration()
   {
      AxisAngle chestOrientationAA = new AxisAngle(0.0, 1.0, 0.0, Math.toRadians(10.0));
      Quaternion chestOrientation = new Quaternion(chestOrientationAA);
      ChestTrajectoryMessage chestOrientationTrajectoryMessage = new ChestTrajectoryMessage(armTrajectoryTime, chestOrientation);
      sendPacket(chestOrientationTrajectoryMessage);
      
      AxisAngle pelvisOrientationAA = new AxisAngle(0.0, 1.0, 0.0, Math.toRadians(-15.0));
      Quaternion pelvisOrientation = new Quaternion(pelvisOrientationAA);
      PelvisOrientationTrajectoryMessage pelvisOrientationTrajectoryMessage = new PelvisOrientationTrajectoryMessage(armTrajectoryTime, pelvisOrientation);
      sendPacket(pelvisOrientationTrajectoryMessage);
      
      ReferenceFrame pelvisFrame = referenceFrames.getPelvisFrame();
      FramePose pelvisPose = new FramePose(pelvisFrame);
      pelvisPose.setZ(-0.04);
      pelvisPose.changeFrame(ReferenceFrame.getWorldFrame());
      Point3D pelvisGoalLocation = new Point3D();
      pelvisPose.getPose(pelvisGoalLocation, new Quaternion());

      PelvisHeightTrajectoryMessage pelvisHeightTrajectoryMessage = new PelvisHeightTrajectoryMessage(armTrajectoryTime, pelvisGoalLocation.getZ());
      sendPacket(pelvisHeightTrajectoryMessage);
      
      ArmTrajectoryMessage leftArmTrajectoryMessage = new ArmTrajectoryMessage(RobotSide.LEFT, armTrajectoryTime, leftArmGoalPosition);
      ArmTrajectoryMessage rightArmTrajectoryMessage = new ArmTrajectoryMessage(RobotSide.RIGHT, armTrajectoryTime, rightArmGoalPosition);
      
      sendPacket(leftArmTrajectoryMessage);
      sendPacket(rightArmTrajectoryMessage);

      robotConfigurationInitialized = true;
   }
   
   public void makeFirstStepThroughHatchOpening()
   {
      //Do this when restructuring code
//      FramePoint startPointInFrontOfHatch = new FramePoint(hatchFrame, -0.1, 0.0, 0.0);
//      startPointInFrontOfHatch.changeFrame(worldFrame);
      
      // Not present for 4.0 time
      if(version == 1 || version == 2)
      {
         AxisAngle chestOrientationAA = new AxisAngle(0.0, 1.0, 0.0, Math.toRadians(25.0)); // was 10.0
         Quaternion chestOrientation = new Quaternion(chestOrientationAA);
         ChestTrajectoryMessage chestOrientationTrajectoryMessage = new ChestTrajectoryMessage(armTrajectoryTime, chestOrientation);
         sendPacket(chestOrientationTrajectoryMessage);
      }
      
      
      
      AxisAngle pelvisOrientationAA = new AxisAngle(0.0, 1.0, 0.0, Math.toRadians(-5.0)); // was now 0!!! //was 10.0, (might have) worked
      Quaternion pelvisOrientation = new Quaternion(pelvisOrientationAA);
      double factor = 1.5;
      if(version == 1)
      {
         factor = 2.5;
      }
      else if(version == 2)
      {
         factor = 4.5;
      }
      PelvisOrientationTrajectoryMessage pelvisOrientationTrajectoryMessage = new PelvisOrientationTrajectoryMessage(factor*armTrajectoryTime, pelvisOrientation); // was 1.5
      sendPacket(pelvisOrientationTrajectoryMessage);
      
//      ReferenceFrame pelvisFrame = referenceFrames.getPelvisFrame();
//      FramePose pelvisPose = new FramePose(pelvisFrame);
//      pelvisPose.setZ(0.04);
//      pelvisPose.changeFrame(ReferenceFrame.getWorldFrame());
//      Point3D pelvisGoalLocation = new Point3D();
//      pelvisPose.getPose(pelvisGoalLocation, new Quaternion());
//
//      PelvisHeightTrajectoryMessage pelvisHeightTrajectoryMessage = new PelvisHeightTrajectoryMessage(1.5*armTrajectoryTime, pelvisGoalLocation.getZ());
//      sendPacket(pelvisHeightTrajectoryMessage);
      
      FootstepDataListMessage footsteps = new FootstepDataListMessage(swingTime.getDoubleValue(), transferTime.getDoubleValue());
      footsteps.setExecutionMode(ExecutionMode.OVERRIDE);
      footsteps.setDestination(PacketDestination.BROADCAST);

      ReferenceFrame rightSoleFrame = referenceFrames.getSoleFrame(RobotSide.RIGHT);
      FramePose stepPose = new FramePose(rightSoleFrame);
      stepPose.setX(0.58);
      stepPose.setY(0.08);

      stepPose.changeFrame(ReferenceFrame.getWorldFrame());

      Point3D location = new Point3D();
      Quaternion orientation = new Quaternion();
      stepPose.getPose(location, orientation);

      FootstepDataMessage footstepData = new FootstepDataMessage(RobotSide.RIGHT, location, orientation);
      footstepData.setOrigin(FootstepOrigin.AT_SOLE_FRAME);
      footsteps.add(footstepData);
      
      footstepData.setSwingHeight(0.17);

      sendPacket(footsteps);
      madeFirstStepThroughHatch = true;
   }
   
   public void changeRobotConfiguration()
   {
      AxisAngle pelvisGoalOrientationYAA = new AxisAngle(0.0, 1.0, 0.0, Math.toRadians(10.0));
      Quaternion pelvisGoalOrientationY = new Quaternion(pelvisGoalOrientationYAA);
      AxisAngle pelvisGoalOrientationXAA = new AxisAngle(1.0, 0.0, 0.0, Math.toRadians(0.0));
      Quaternion pelvisGoalOrientationX = new Quaternion(pelvisGoalOrientationXAA);
      
      Quaternion pelvisGoalOrientation = new Quaternion();
      pelvisGoalOrientation.multiply(pelvisGoalOrientationX, pelvisGoalOrientationY);
      
      ReferenceFrame pelvisFrame = referenceFrames.getPelvisFrame();
      FramePose pelvisPose = new FramePose(pelvisFrame);
      pelvisPose.setX(0.15); // was 0.12 before test
      pelvisPose.setZ(-7.0); // Added as test! 5
      if(version == 0)
      {
       pelvisPose.setZ(0.05); // present for 4.0
      }
      pelvisPose.changeFrame(ReferenceFrame.getWorldFrame());
      Point3D pelvisGoalLocation = new Point3D();
      
      pelvisPose.getPose(pelvisGoalLocation, new Quaternion());

      PelvisTrajectoryMessage pelvisTrajectoryMessage = new PelvisTrajectoryMessage(armTrajectoryTime, pelvisGoalLocation, pelvisGoalOrientation);
      sendPacket(pelvisTrajectoryMessage);
      
      changedRobotConfiguration = true;
   }
   
   public void makeSecondStepThroughHatchOpening()
   {
      // Not present for 4.0
      if(version == 1 || version == 2)
      {
         ReferenceFrame pelvisFrame = referenceFrames.getPelvisFrame();
         FramePose pelvisPose = new FramePose(pelvisFrame);
         pelvisPose.setZ(0.05);
         pelvisPose.changeFrame(ReferenceFrame.getWorldFrame());
         Point3D pelvisGoalLocation = new Point3D();
         pelvisPose.getPose(pelvisGoalLocation, new Quaternion());
         PelvisHeightTrajectoryMessage pelvisHeightTrajectoryMessage = new PelvisHeightTrajectoryMessage(armTrajectoryTime, pelvisGoalLocation.getZ());
         sendPacket(pelvisHeightTrajectoryMessage);
      }
      
      FootstepDataListMessage footsteps = new FootstepDataListMessage(swingTime.getDoubleValue(), transferTime.getDoubleValue());
      footsteps.setExecutionMode(ExecutionMode.OVERRIDE);
      footsteps.setDestination(PacketDestination.BROADCAST);

      ReferenceFrame leftSoleFrame = referenceFrames.getSoleFrame(RobotSide.LEFT);
      FramePose stepPose = new FramePose(leftSoleFrame);
      stepPose.setX(0.58);
      stepPose.setY(0.00);

      stepPose.changeFrame(ReferenceFrame.getWorldFrame());

      Point3D location = new Point3D();
      Quaternion orientation = new Quaternion();
      stepPose.getPose(location, orientation);

      FootstepDataMessage footstepData = new FootstepDataMessage(RobotSide.LEFT, location, orientation);
      footstepData.setOrigin(FootstepOrigin.AT_SOLE_FRAME);
      
      
      FramePose wayPointPose1 = new FramePose(leftSoleFrame);
      wayPointPose1.setX(0.06);
      wayPointPose1.setY(-0.06);
      wayPointPose1.setZ(0.25);
      wayPointPose1.changeFrame(ReferenceFrame.getWorldFrame());
      Point3D locationWayPoint1 = new Point3D();
      wayPointPose1.getPose(locationWayPoint1, new Quaternion());
      
      FramePose wayPointPose2 = new FramePose(leftSoleFrame);
      wayPointPose2.setX(0.45);
      wayPointPose2.setY(0.00);
      wayPointPose2.setZ(0.19);
      wayPointPose2.changeFrame(ReferenceFrame.getWorldFrame());
      Point3D locationWayPoint2 = new Point3D();
      wayPointPose2.getPose(locationWayPoint2, new Quaternion());
      
      footstepData.setTrajectoryType(TrajectoryType.CUSTOM);
      footstepData.setTrajectoryWaypoints(new Point3D[] {locationWayPoint1, locationWayPoint2});
      footsteps.add(footstepData);
      
      //footstepData.setSwingHeight(0.19);

      sendPacket(footsteps);
      madeSecondStepThroughHatch = true;
   }
   
   @Override
   public void onBehaviorEntered()
   {
      abortBehavior.set(false);
      stepInPlace.set(true);
      sendPacket(new TextToSpeechPacket("Starting to step forward and backward with the right foot."));
   }

   @Override
   public void onBehaviorAborted()
   {
      // TODO Auto-generated method stub

   }

   @Override
   public void onBehaviorPaused()
   {
      // TODO Auto-generated method stub

   }

   @Override
   public void onBehaviorResumed()
   {
      // TODO Auto-generated method stub

   }

   @Override
   public void onBehaviorExited()
   {
      // TODO Auto-generated method stub

   }

   @Override
   public boolean isDone()
   {
      return abortBehavior.getBooleanValue();
   }
}