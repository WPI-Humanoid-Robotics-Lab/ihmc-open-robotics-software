package us.ihmc.llaQuadruped.generatedTestSuites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite.SuiteClasses;

import us.ihmc.tools.continuousIntegration.ContinuousIntegrationSuite;
import us.ihmc.tools.continuousIntegration.ContinuousIntegrationSuite.ContinuousIntegrationSuiteCategory;
import us.ihmc.tools.continuousIntegration.IntegrationCategory;

/** WARNING: AUTO-GENERATED FILE. DO NOT MAKE MANUAL CHANGES TO THIS FILE. **/
@RunWith(ContinuousIntegrationSuite.class)
@ContinuousIntegrationSuiteCategory(IntegrationCategory.IN_DEVELOPMENT)
@SuiteClasses
({
   us.ihmc.llaQuadruped.controller.force.LLAQuadrupedXGaitBumpyTerrainWalkingTest.class,
   us.ihmc.llaQuadruped.controller.force.LLAQuadrupedXGaitFlatGroundPaceTest.class
})

public class LLAQuadrupedBInDevelopmentTestSuite
{
   public static void main(String[] args)
   {

   }
}