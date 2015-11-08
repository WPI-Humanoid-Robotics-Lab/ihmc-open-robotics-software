package us.ihmc.quadrupedRobotics.footstepChooser;

public class DefaultSwingTargetGeneratorParameters implements SwingTargetGeneratorParameters 
{
   private final double minimumVelocityForFullSkew = 0.1;
   private final double minimumDistanceFromSameSideFoot = 0.04;
   private final double stanceLength = 0.34;
   private final double stanceWidth = 0.24;
   private final double maxSkew = 0.29;
   private final double maxYawPerStep = 0.25;
   
   @Override
   public double getMinimumVelocityForFullSkew()
   {
      return minimumVelocityForFullSkew;
   }

   @Override
   public double getMinimumDistanceFromSameSideFoot()
   {
      return minimumDistanceFromSameSideFoot;
   }

   @Override
   public double getStanceLength()
   {
      return stanceLength;
   }

   @Override
   public double getStanceWidth()
   {
      return stanceWidth;
   }

   @Override
   public double getMaxSkew()
   {
      return maxSkew;
   }

   @Override
   public double getMaxYawPerStep()
   {
      return maxYawPerStep;
   }
}