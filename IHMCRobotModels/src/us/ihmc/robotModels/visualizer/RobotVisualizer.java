package us.ihmc.robotModels.visualizer;

import us.ihmc.robotModels.FullRobotModel;
import us.ihmc.robotics.dataStructures.registry.YoVariableRegistry;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.YoGraphicsListRegistry;

public interface RobotVisualizer
{
   public void update(long timestamp);

   public void update(long timestamp, YoVariableRegistry registry);

   public void setMainRegistry(YoVariableRegistry registry, FullRobotModel fullRobotModel, YoGraphicsListRegistry yoGraphicsListRegistry);

   public void addRegistry(YoVariableRegistry registry, YoGraphicsListRegistry yoGraphicsListRegistry);

   public void close();
}