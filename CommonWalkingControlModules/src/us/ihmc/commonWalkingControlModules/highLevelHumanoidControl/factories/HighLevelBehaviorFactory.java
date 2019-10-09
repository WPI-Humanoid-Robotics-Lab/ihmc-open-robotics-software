package us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.factories;

import us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.highLevelStates.HighLevelBehavior;
import us.ihmc.commonWalkingControlModules.momentumBasedController.HighLevelHumanoidControllerToolbox;

public interface HighLevelBehaviorFactory
{
   public abstract HighLevelBehavior createHighLevelBehavior(HighLevelControlManagerFactory variousWalkingManagers, HighLevelHumanoidControllerToolbox momentumBasedController);

   public abstract boolean isTransitionToBehaviorRequested();
}