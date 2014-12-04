package us.ihmc.valkyrie.logProcessor;

import java.io.IOException;

import us.ihmc.darpaRoboticsChallenge.drcRobot.DRCRobotModel;
import us.ihmc.darpaRoboticsChallenge.logProcessor.DRCLogProcessor;
import us.ihmc.darpaRoboticsChallenge.logProcessor.LogDataProcessorFunction;
import us.ihmc.valkyrie.ValkyrieRobotModel;

public class ValkyrieDiagnosticLogProcessor extends DRCLogProcessor
{
   public ValkyrieDiagnosticLogProcessor() throws IOException
   {
      super();
      LogDataProcessorFunction logDataProcessor = new DiagnosticLogProcessorFunction(logDataProcessorHelper);
      setLogDataProcessor(logDataProcessor);
      startLogger();
   }

   public static void main(String[] args) throws IOException
   {
      new ValkyrieDiagnosticLogProcessor();
   }

   @Override
   public DRCRobotModel createDRCRobotModel()
   {
      return new ValkyrieRobotModel(false, false);
   }
}
