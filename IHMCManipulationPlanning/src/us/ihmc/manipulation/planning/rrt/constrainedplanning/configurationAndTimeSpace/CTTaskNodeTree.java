package us.ihmc.manipulation.planning.rrt.constrainedplanning.configurationAndTimeSpace;

import java.util.ArrayList;
import java.util.Random;

import us.ihmc.commons.PrintTools;

public class CTTaskNodeTree
{
   private CTTaskNode rootNode;
   private CTTaskNode nearNode;
   private CTTaskNode newNode;
   private CTTaskNode randomNode;
   
   private ArrayList<CTTaskNode> path = new ArrayList<CTTaskNode>();
   
   private ArrayList<CTTaskNode> wholeNodes = new ArrayList<CTTaskNode>();
   private ArrayList<CTTaskNode> failNodes = new ArrayList<CTTaskNode>();
   
   private CTTaskNodeRegion nodeRegion;
   
   /*
    * If @param matricRatioTimeToTask is 0.3, the matric will be obtained as much as (getNormalizedTimeGap*0.3 + getNormalizedTaskDisplacement*0.7).
    */   
   private double matricRatioTimeToTask = 0.5;
   
   private double maximumDisplacementOfStep = 0.15;
   private double maximumTimeGapOfStep = 0.03;
   
   private int dimensionOfTask;

   private ArrayList<String> taskNames;
   
   private double trajectoryTime;
   
   public CTTaskNodeTree(CTTaskNode rootNode)
   {
      this.rootNode = rootNode;
      this.wholeNodes.add(this.rootNode);
      
      this.nodeRegion = new CTTaskNodeRegion(this.rootNode.getDimensionOfNodeData());
      
      this.dimensionOfTask = rootNode.getDimensionOfNodeData()-1;
      
      this.taskNames = new ArrayList<String>();
      this.taskNames.add("time");
      for(int i=1;i<this.dimensionOfTask+1;i++)
         this.taskNames.add("Task_"+i+"_");
   }
   
   public CTTaskNodeTree(CTTaskNode rootNode, String... taskNames)
   {
      this.rootNode = rootNode;
      this.wholeNodes.add(this.rootNode);
      
      this.nodeRegion = new CTTaskNodeRegion(this.rootNode.getDimensionOfNodeData());
      
      this.dimensionOfTask = rootNode.getDimensionOfNodeData()-1;
      
      this.taskNames = new ArrayList<String>();
      this.taskNames.add("time");
      if(this.dimensionOfTask != taskNames.length)
         PrintTools.warn("Task dimension is incorrect");
      else
         for(int i=1;i<this.dimensionOfTask+1;i++)
            this.taskNames.add("Task_"+i+"_"+taskNames[i-1]);
   }
      
   public String getTaskName(int indexOfDimension)
   {
      return taskNames.get(indexOfDimension);
   }   
   
   public int getDimensionOfTask()
   {
      return dimensionOfTask;
   }
      
   public double getTrajectoryTime()
   {
      trajectoryTime = nodeRegion.getTrajectoryTime();
      return trajectoryTime;
   }
      
   private void setRandomNormalizedNodeData(CTTaskNode node, int index)
   {
      Random randomManager = new Random();
      double value = 0;
      if(index==0)
      {
         value = randomManager.nextDouble()*nodeRegion.getIntentionalTimeRatio();
      }
      else
      {
         value = randomManager.nextDouble() - 0.5;
      }
      node.setNormalizedNodeData(index, value);
   }
    
   private void setRandomNormalizedNodeData(CTTaskNode node)
   {
      for(int i=0;i<node.getDimensionOfNodeData();i++)
         setRandomNormalizedNodeData(node, i);
   }
   
   public void setMatricRatioTimeToTask(double ratio)
   {
      matricRatioTimeToTask = ratio;
   }
   
   public void expandTree(int numberOfExpanding)
   {
      for(int i=0;i<numberOfExpanding;i++)
      {
         PrintTools.info("expanding process "+i);         
         if(expandingTree())
         {
            PrintTools.info("expanding is done "+i);
            ArrayList<CTTaskNode> revertedPath = new ArrayList<CTTaskNode>();
            CTTaskNode currentNode = newNode;
            revertedPath.add(currentNode);            
            
            while(true)
            {
               currentNode = currentNode.getParentNode();
               if(currentNode != null)
               {
                  revertedPath.add(currentNode);                  
               }
               else
                  break;
            }
            
            int revertedPathSize = revertedPath.size();
            
            path.clear();
            for(int j=0;j<revertedPathSize;j++)
               path.add(revertedPath.get(revertedPathSize - 1 - j));
            
            PrintTools.info("Constructed Tree size is "+revertedPathSize);
            
            break;
         }
      }
   }
   
   private double getNormalizedTaskDisplacement(CTTaskNode nodeOne, CTTaskNode nodeTwo)
   {
      double squaredDisplacement = 0;
      
      for(int i=1;i<rootNode.getDimensionOfNodeData();i++)
      {
         double nodeOneValue = nodeOne.getNormalizedNodeData(i);
         double nodeTwoValue = nodeTwo.getNormalizedNodeData(i);
         squaredDisplacement = squaredDisplacement + (nodeOneValue - nodeTwoValue) * (nodeOneValue - nodeTwoValue);
      }      
      
      return Math.sqrt(squaredDisplacement);
   }
   
   private double getNormalizedTimeGap(CTTaskNode nodeOld, CTTaskNode nodeNew)
   {
      return nodeNew.getNormalizedNodeData(0) - nodeOld.getNormalizedNodeData(0);
   }
   
   private double getMatric(CTTaskNode nodeOne, CTTaskNode nodeTwo)
   {      
      double normalizedtimeGap = getNormalizedTimeGap(nodeOne, nodeTwo);
      
      if(normalizedtimeGap <= 0)
         return Double.MAX_VALUE;
      else
      {
         double matric = 0;
         matric = normalizedtimeGap * matricRatioTimeToTask + getNormalizedTaskDisplacement(nodeOne, nodeTwo) * (1-matricRatioTimeToTask);
         return matric;
      }
   }
   
   private CTTaskNode createNode()
   {
      return rootNode.createNode();
   }
   
   /*
    * As long as the tree does not reach the trajectory time, this method returns false.
    */
   private boolean expandingTree()
   {
      updateRandomConfiguration();
      updateNearestNode();
      updateNewConfiguration();
      if(connectNewConfiguration())
      {
         if(this.newNode.getTime() == getTrajectoryTime())
            return true;
         else
            return false;
      }
      else
      {
         return false;
      }
   }
   
   private void updateRandomConfiguration()
   {
      CTTaskNode randomNode = createNode();
      setRandomNormalizedNodeData(randomNode);
      this.randomNode = randomNode;
   }
   
   private void updateNearestNode()
   {
      CTTaskNode nearNode = this.rootNode;
      CTTaskNode curNode;
      
      double optMatric = Double.MAX_VALUE;
      double curMatric;
            
      for(int i=0;i<wholeNodes.size();i++)
      {
         curNode = this.wholeNodes.get(i);
         curMatric = getMatric(curNode, randomNode);
         if (curMatric < optMatric)
         {
            optMatric = curMatric;
            nearNode = curNode;
         }   
      }
      
      this.nearNode = nearNode;
   }
   
   private void updateNewConfiguration()
   {
      CTTaskNode newNode = createNode();
      
      double timeGap = getNormalizedTimeGap(this.nearNode, this.randomNode);
      double displacement = getNormalizedTaskDisplacement(this.nearNode, this.randomNode); 
      
      double expandedTime;
      double expandingTimeGap;
      double expandingDisplacement;      
            
      /*
       *  timeGap Clamping
       */
      if(timeGap > maximumTimeGapOfStep)
      {
         expandingTimeGap = maximumTimeGapOfStep;
      }
      else
      {
         expandingTimeGap = timeGap;         
      }
      
      /* 
       *  maximumDisplacementOfStep
       *  check the expandedTime is over 1.0 and if then, Clamping again.
       */
      expandedTime = nearNode.getNormalizedNodeData(0) + expandingTimeGap;
      if(expandedTime > 1.0)
      {
         expandedTime = 1.0;
         expandingTimeGap = expandedTime - nearNode.getNormalizedNodeData(0);
      }
      
      /*
       *  1st displacement Clamping under expandingTimeGap.
       */
      expandingDisplacement = displacement*expandingTimeGap/timeGap;
      
      /*
       *  2nd displacement Clamping
       */
      if(expandingDisplacement > maximumDisplacementOfStep)
      {
         expandingDisplacement = maximumDisplacementOfStep;
         expandingTimeGap = timeGap*maximumDisplacementOfStep/displacement;
      }
      
      /*
       *  set
       */
      newNode.setNormalizedNodeData(0, nearNode.getNormalizedNodeData(0) + expandingTimeGap);
      for(int i=1;i<newNode.getDimensionOfNodeData();i++)
      {
         double iDisplacement = (this.randomNode.getNormalizedNodeData(i) - nearNode.getNormalizedNodeData(i))/displacement*expandingDisplacement;
         newNode.setNormalizedNodeData(i, nearNode.getNormalizedNodeData(i) + iDisplacement);
//         PrintTools.info("expandingDisplacement "+ displacement + " " + expandingDisplacement + " " + iDisplacement +" "+(this.randomNode.getNormalizedNodeData(i) - nearNode.getNormalizedNodeData(i)));
      }     
      
//      for (int i = 0; i < this.randomNode.getDimensionOfNodeData(); i++)
//      {
//         PrintTools.info("randomNode "+randomNode.getNormalizedNodeData(i) + " ");
//      }
//      for (int i = 0; i < nearNode.getDimensionOfNodeData(); i++)
//      {
//         PrintTools.info("nearNode "+nearNode.getNormalizedNodeData(i) + " ");
//      }
//      for (int i = 0; i < newNode.getDimensionOfNodeData(); i++)
//      {
//         PrintTools.info("newNode "+newNode.getNormalizedNodeData(i) + " ");
//      }
      
      this.newNode = newNode;
   }
   
   /*
    * When the new configuration is valid, return true.
    */
   private boolean connectNewConfiguration()
   {
      this.newNode.convertNormalizedDataToData(nodeRegion);
      this.newNode.setParentNode(this.nearNode);
      
      if(this.newNode.isValidNode())
      {
         nearNode.addChildNode(this.newNode);
         wholeNodes.add(newNode);
//         PrintTools.info("this new Configuration is added on tree");      
         for (int i = 0; i < this.newNode.getDimensionOfNodeData(); i++)
         {
//            PrintTools.info("randomNode "+newNode.getNodeData(i) + " ");
         }
         return true;
      }
      else
      {
         this.newNode.clearParentNode();
         failNodes.add(this.newNode);
//         PrintTools.info("this new Configuration cannot be added on tree");
         return false;
      }
   }
   
   public CTTaskNodeRegion getTaskNodeRegion()
   {
      return nodeRegion;
   }

   public ArrayList<CTTaskNode> getPath()
   {
      return path;
   }
   
   public ArrayList<CTTaskNode> getWholeNodes()
   {
      return wholeNodes;
   }
   
   public ArrayList<CTTaskNode> getFailNodes()
   {
      return failNodes;
   }
   
//   public void saveNodes()
//   {
//      String fileName = "/home/shadylady/tree.txt";
//      BufferedWriter bw = null;
//      FileWriter fw = null;
//      
//      System.out.println("Save Start");
//      try {
//         String savingContent = "";
//         
//         /*
//          * whole nodes
//          */
//         for(int i=0;i<getWholeNodes().size();i++)
//         {
//            String convertedNodeData = "";            
//            
//            convertedNodeData = convertedNodeData + "1\t";
//            for(int j=0;j<getWholeNodes().get(i).getDimensionOfNodeData();j++)
//            {
//               convertedNodeData = convertedNodeData + String.format("%.3f\t", getWholeNodes().get(i).getNodeData(j));               
//            }
//            
//            if(getWholeNodes().get(i).getParentNode() == null)
//            {
//               for(int j=0;j<getWholeNodes().get(i).getDimensionOfNodeData();j++)
//               {
//                  convertedNodeData = convertedNodeData + "0\t";               
//               }
//            }
//            else
//            {
//               for(int j=0;j<getWholeNodes().get(i).getDimensionOfNodeData();j++)
//               {
//                  convertedNodeData = convertedNodeData + String.format("%.3f\t", getWholeNodes().get(i).getParentNode().getNodeData(j));               
//               }   
//            }
//            convertedNodeData = convertedNodeData + "\n";
//            
//            savingContent = savingContent + convertedNodeData;
//         }
//         
//         /*
//          * fail nodes
//          */
//         for(int i=0;i<getFailNodes().size();i++)
//         {
//            String convertedNodeData = "";            
//            
//            convertedNodeData = convertedNodeData + "2\t";
//            for(int j=0;j<getFailNodes().get(i).getDimensionOfNodeData();j++)
//            {
//               convertedNodeData = convertedNodeData + String.format("%.3f\t", getFailNodes().get(i).getNodeData(j));               
//            }
//            convertedNodeData = convertedNodeData + "\n";
//            
//            savingContent = savingContent + convertedNodeData;
//         }
//         
//         /*
//          * path nodes
//          */
//         if(getPath().size() > 1)
//         {
//            for(int i=0;i<getPath().size();i++)
//            {
//               String convertedNodeData = "";            
//               
//               convertedNodeData = convertedNodeData + "3\t";
//               for(int j=0;j<getPath().get(i).getDimensionOfNodeData();j++)
//               {
//                  convertedNodeData = convertedNodeData + String.format("%.3f\t", getPath().get(i).getNodeData(j));               
//               }
//               convertedNodeData = convertedNodeData + "\n";
//               
//               savingContent = savingContent + convertedNodeData;
//            }
//         }
//         
//         fw = new FileWriter(fileName);
//         bw = new BufferedWriter(fw);
//         bw.write(savingContent);
//
//         System.out.println("Save Done");
//
//      } catch (IOException e) {
//
//         e.printStackTrace();
//
//      } finally {
//
//         try {
//
//            if (bw != null)
//               bw.close();
//
//            if (fw != null)
//               fw.close();
//
//         } catch (IOException ex) {
//
//            ex.printStackTrace();
//
//         }
//
//      }
//   }
}