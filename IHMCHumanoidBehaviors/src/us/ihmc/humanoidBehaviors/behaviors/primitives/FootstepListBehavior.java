package us.ihmc.humanoidBehaviors.behaviors.primitives;

import java.util.ArrayList;

import us.ihmc.commonWalkingControlModules.configurations.WalkingControllerParameters;
import us.ihmc.commons.PrintTools;
import us.ihmc.communication.packets.PacketDestination;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.euclid.tuple3D.Vector3D;
import us.ihmc.humanoidBehaviors.behaviors.AbstractBehavior;
import us.ihmc.humanoidBehaviors.communication.CommunicationBridgeInterface;
import us.ihmc.humanoidBehaviors.communication.ConcurrentListeningQueue;
import us.ihmc.humanoidRobotics.communication.packets.walking.FootstepDataListMessage;
import us.ihmc.humanoidRobotics.communication.packets.walking.FootstepDataMessage;
import us.ihmc.humanoidRobotics.communication.packets.walking.FootstepStatus;
import us.ihmc.humanoidRobotics.communication.packets.walking.PauseWalkingMessage;
import us.ihmc.humanoidRobotics.communication.packets.walking.WalkingStatusMessage;
import us.ihmc.humanoidRobotics.footstep.Footstep;
import us.ihmc.robotModels.FullHumanoidRobotModel;
import us.ihmc.robotics.geometry.FrameOrientation;
import us.ihmc.robotics.geometry.FramePoint;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.yoVariables.variable.YoBoolean;
import us.ihmc.yoVariables.variable.YoInteger;

public class FootstepListBehavior extends AbstractBehavior
{
   private static final boolean DEBUG = false;

   private FootstepDataListMessage outgoingFootstepDataList;
   private final ConcurrentListeningQueue<FootstepStatus> footstepStatusQueue;
   private final ConcurrentListeningQueue<WalkingStatusMessage> walkingStatusQueue;

   private final YoBoolean packetHasBeenSent = new YoBoolean("packetHasBeenSent" + behaviorName, registry);
   private final YoInteger numberOfFootsteps = new YoInteger("numberOfFootsteps" + behaviorName, registry);
   private final YoBoolean isPaused = new YoBoolean("isPaused", registry);
   private final YoBoolean isStopped = new YoBoolean("isStopped", registry);
   private final YoBoolean isDone = new YoBoolean("isDone", registry);
   private final YoBoolean hasLastStepBeenReached = new YoBoolean("hasLastStepBeenReached", registry);
   private final YoBoolean isRobotDoneWalking = new YoBoolean("isRobotDoneWalking", registry);
   private final YoBoolean hasRobotStartedWalking = new YoBoolean("hasRobotStartedWalking", registry);


   private double defaultSwingTime;
   private double defaultTranferTime;

   public FootstepListBehavior(CommunicationBridgeInterface outgoingCommunicationBridge, WalkingControllerParameters walkingControllerParameters)
   {
      super(outgoingCommunicationBridge);
      footstepStatusQueue = new ConcurrentListeningQueue<FootstepStatus>(40);
      attachNetworkListeningQueue(footstepStatusQueue, FootstepStatus.class);
      walkingStatusQueue = new ConcurrentListeningQueue<>(40);
      attachNetworkListeningQueue(walkingStatusQueue, WalkingStatusMessage.class);
      numberOfFootsteps.set(-1);
      defaultSwingTime = walkingControllerParameters.getDefaultSwingTime();
      defaultTranferTime = walkingControllerParameters.getDefaultTransferTime();
   }



   public void set(FootstepDataListMessage footStepList)
   {
      outgoingFootstepDataList = footStepList;
      numberOfFootsteps.set(outgoingFootstepDataList.getDataList().size());
      packetHasBeenSent.set(false);
   }


   public void set(ArrayList<Footstep> footsteps, double swingTime, double transferTime)
   {
      FootstepDataListMessage footstepDataList = new FootstepDataListMessage(swingTime,transferTime);

      for (int i = 0; i < footsteps.size(); i++)
      {
         Footstep footstep = footsteps.get(i);
         FramePoint position = new FramePoint();
         FrameOrientation orientation = new FrameOrientation();
         footstep.getPose(position, orientation);

         RobotSide footstepSide = footstep.getRobotSide();
         FootstepDataMessage footstepData = new FootstepDataMessage(footstepSide, position.getPoint(), orientation.getQuaternion());
         footstepDataList.add(footstepData);
      }
      set(footstepDataList);
   }

   public void set(ArrayList<Footstep> footsteps)
   {
      set(footsteps, defaultSwingTime, defaultTranferTime);
   }


   @Override
   public void doControl()
   {
      checkForNewStatusPacket();

      if (!packetHasBeenSent.getBooleanValue() && outgoingFootstepDataList != null)
      {
         sendFootsepListToController();
      }
   }

   private void sendFootsepListToController()
   {
      if (!isPaused.getBooleanValue() && !isStopped.getBooleanValue())
      {
         outgoingFootstepDataList.setDestination(PacketDestination.UI);
         sendPacket(outgoingFootstepDataList);

         outgoingFootstepDataList.setDestination(PacketDestination.CONTROLLER);
         sendPacketToController(outgoingFootstepDataList);
         packetHasBeenSent.set(true);
      }
   }

   private void checkForNewStatusPacket()
   {
      if (footstepStatusQueue.isNewPacketAvailable())
      {
         FootstepStatus newestFootstepStatus = footstepStatusQueue.poll();
         if (newestFootstepStatus != null)
         {
            int currentStepIndex = newestFootstepStatus.getFootstepIndex();
            int currentStepNumber = currentStepIndex + 1;

            if (currentStepNumber >= numberOfFootsteps.getIntegerValue())
               hasLastStepBeenReached.set(true);

            if (DEBUG)
            {
               PrintTools.debug(this, "** \n Recieved new FootStepStatus : " + newestFootstepStatus);
               PrintTools.debug(this, "current footstep number: " + currentStepNumber + ", total number of footsteps: " + numberOfFootsteps.getIntegerValue());
               PrintTools.debug(this, "Reached last step? : " + hasLastStepBeenReached.getBooleanValue());
               PrintTools.debug(this, "FootstepStatus: is done walking? : " + isRobotDoneWalking.getBooleanValue());

               if (!isDone.getBooleanValue() && isDone())
               {
                  PrintTools.debug(this, "*****  Setting isDone = true  *******");
               }
               else
               {
                  PrintTools.debug(this, "is Behavior done? : " + isDone.getBooleanValue());
               }
            }
         }
      }

      if (walkingStatusQueue.isNewPacketAvailable())
      {
         WalkingStatusMessage newestPacket = walkingStatusQueue.poll();
         if (newestPacket != null)
         {
            switch (newestPacket.getWalkingStatus())
            {
            case COMPLETED:
               isRobotDoneWalking.set(true);
               break;
            case STARTED:
               hasRobotStartedWalking.set(true);
               break;
            default:
               break;
            }
         }
      }
   }

   @Override
   public void onBehaviorEntered()
   {
      if (DEBUG)
         PrintTools.debug(this, "Initialize");
      packetHasBeenSent.set(false);
      hasLastStepBeenReached.set(false);
      isRobotDoneWalking.set(false);

      isPaused.set(false);
      isStopped.set(false);
      hasBeenInitialized.set(true);
      hasRobotStartedWalking.set(false);
   }

   @Override
   public void onBehaviorExited()
   {
      if (DEBUG)
         PrintTools.debug(this, "Finalize");
      footstepStatusQueue.clear();
      outgoingFootstepDataList = null;
      packetHasBeenSent.set(false);
      numberOfFootsteps.set(-1);

      isPaused.set(false);
      isStopped.set(false);
      hasBeenInitialized.set(false);
      hasLastStepBeenReached.set(false);
      isRobotDoneWalking.set(false);
      hasRobotStartedWalking.set(false);
   }

   @Override
   public void onBehaviorAborted()
   {
      sendPacketToController(new PauseWalkingMessage(true));
      isPaused.set(true);
      isStopped.set(true);
   }

   @Override
   public void onBehaviorPaused()
   {
      sendPacketToController(new PauseWalkingMessage(true));
      isPaused.set(true);
      if (DEBUG)
         PrintTools.debug(this, "Pausing Behavior");
   }

   @Override
   public void onBehaviorResumed()
   {
      sendPacketToController(new PauseWalkingMessage(false));
      isPaused.set(false);
      isStopped.set(false);
      isRobotDoneWalking.set(false);
      if (DEBUG)
         PrintTools.debug(this, "Resuming Behavior");
   }

   @Override
   public boolean isDone()
   {
//      System.out.println("isDone "+isRobotDoneWalking.getBooleanValue() + " " +isPaused.getBooleanValue());
      boolean ret = isRobotDoneWalking.getBooleanValue() && !isPaused.getBooleanValue();
      if (!isDone.getBooleanValue() && ret)
      {
         if (DEBUG)
            PrintTools.debug(this, "*****  Setting isDone = true  *******");
      }
      isDone.set(ret);
      return ret;
   }



   public boolean hasInputBeenSet()
   {
      if (numberOfFootsteps.getIntegerValue() != -1 && hasRobotStartedWalking.getBooleanValue())
         return true;
      else
         return false;
   }

   public boolean isWalking()
   {
      return hasInputBeenSet() && !isDone();
   }

   private final ArrayList<FootstepDataMessage> footstepDataList = new ArrayList<FootstepDataMessage>();
   private final Vector3D firstSingleSupportFootTranslationFromWorld = new Vector3D();
   private final Point3D previousFootStepLocation = new Point3D();
   private final Point3D nextFootStepLocation = new Point3D();

   public ArrayList<Double> getFootstepLengths(FootstepDataListMessage footStepList, FullHumanoidRobotModel fullRobotModel,
         WalkingControllerParameters walkingControllerParameters)
   {
      ArrayList<Double> footStepLengths = new ArrayList<Double>();
      footstepDataList.addAll(footStepList.getDataList());

      FootstepDataMessage firstStepData = footstepDataList.remove(footstepDataList.size() - 1);

      RigidBodyTransform firstSingleSupportFootTransformToWorld = fullRobotModel.getFoot(firstStepData.getRobotSide().getOppositeSide()).getBodyFixedFrame()
            .getTransformToWorldFrame();
      firstSingleSupportFootTransformToWorld.getTranslation(firstSingleSupportFootTranslationFromWorld);

      previousFootStepLocation.set(firstSingleSupportFootTranslationFromWorld);
      firstStepData.getLocation(nextFootStepLocation);

      while (!footstepDataList.isEmpty())
      {
         footStepLengths.add(previousFootStepLocation.distance(nextFootStepLocation));
         previousFootStepLocation.set(nextFootStepLocation);
         footstepDataList.remove(footstepDataList.size() - 1).getLocation(nextFootStepLocation);
      }

      double lastStepLength = previousFootStepLocation.distance(nextFootStepLocation);
      footStepLengths.add(lastStepLength);

      return footStepLengths;
   }

   public double getDefaultSwingTime()
   {
      return defaultSwingTime;
   }
   public double getDefaultTranferTime()
   {
      return defaultTranferTime;
   }


   public boolean areFootstepsTooFarApart(FootstepDataListMessage footStepList, FullHumanoidRobotModel fullRobotModel, WalkingControllerParameters walkingControllerParameters)
   {
      for (double stepLength : getFootstepLengths(footStepList, fullRobotModel, walkingControllerParameters))
      {
         if (DEBUG)
            PrintTools.debug(this, "step length : " + stepLength + " max step length : " + walkingControllerParameters.getSteppingParameters().getMaxStepLength());
         if (stepLength > walkingControllerParameters.getSteppingParameters().getMaxStepLength())
         {
            return true;
         }
      }

      return false;
   }

}
