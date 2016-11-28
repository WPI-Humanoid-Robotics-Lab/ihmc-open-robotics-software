package us.ihmc.footstepPlanning.graphSearch;

import java.util.ArrayList;

import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import us.ihmc.robotics.MathTools;
import us.ihmc.robotics.geometry.AngleTools;
import us.ihmc.robotics.geometry.PlanarRegion;
import us.ihmc.robotics.geometry.RigidBodyTransform;
import us.ihmc.robotics.robotSide.RobotSide;

public class BipedalFootstepPlannerNode
{
   private RobotSide footstepSide;
   private RigidBodyTransform soleTransform = new RigidBodyTransform();
   private BipedalFootstepPlannerNode parentNode;

   private ArrayList<BipedalFootstepPlannerNode> childrenNodes;
   private double costFromParent;
   private double costToHereFromStart;
   private double estimatedCostToGoal;

   private static final double XY_DISTANCE_THRESHOLD_TO_CONSIDER_NODES_EQUAL = 0.3;
   private static final double YAW_ROTATION_THRESHOLD_TO_CONSIDER_NODES_EQUAL = 0.3;

   private boolean isAtGoal = false;

   public BipedalFootstepPlannerNode(RobotSide footstepSide, RigidBodyTransform soleTransform)
   {
      this.footstepSide = footstepSide;
      this.soleTransform.set(soleTransform);
   }

   public BipedalFootstepPlannerNode(BipedalFootstepPlannerNode nodeToCopy)
   {
      this(nodeToCopy.footstepSide, nodeToCopy.soleTransform);
   }

   public RigidBodyTransform getTransformToParent()
   {
      if (parentNode == null)
         return null;

      RigidBodyTransform transformToParent = new RigidBodyTransform();

      parentNode.getSoleTransform(transformToParent);
      transformToParent.invert();

      transformToParent.multiply(transformToParent, soleTransform);
      return transformToParent;
   }

   public RobotSide getRobotSide()
   {
      return footstepSide;
   }

   public void setIsAtGoal()
   {
      this.isAtGoal = true;
   }

   public boolean isAtGoal()
   {
      return isAtGoal;
   }

   public void getSoleTransform(RigidBodyTransform soleTransformToPack)
   {
      soleTransformToPack.set(soleTransform);
   }

   public Point3d getSolePosition()
   {
      Point3d currentSolePosition = new Point3d();
      soleTransform.transform(currentSolePosition);
      return currentSolePosition;
   }

   public double getSoleYaw()
   {
      Vector3d eulerAngles = new Vector3d();
      soleTransform.getRotationEuler(eulerAngles);
      return eulerAngles.getZ();
   }

   public void transformSoleTransformWithSnapTransformFromZeroZ(RigidBodyTransform snapTransform, PlanarRegion planarRegion)
   {
      // Ignore the z since the snap transform snapped from z = 0. Keep everything else.
      soleTransform.setM23(0.0);
      soleTransform.multiply(snapTransform, soleTransform);
   }

   public BipedalFootstepPlannerNode getParentNode()
   {
      return parentNode;
   }

   public void setParentNode(BipedalFootstepPlannerNode parentNode)
   {
      this.parentNode = parentNode;
   }

   public void addChild(BipedalFootstepPlannerNode childNode)
   {
      if (childrenNodes == null)
      {
         childrenNodes = new ArrayList<>();
      }

      this.childrenNodes.add(childNode);
   }

   public void getChildren(ArrayList<BipedalFootstepPlannerNode> childrenNodesToPack)
   {
      childrenNodesToPack.addAll(childrenNodes);
   }

   public double getCostFromParent()
   {
      return costFromParent;
   }

   public void setCostFromParent(double costFromParent)
   {
      this.costFromParent = costFromParent;
   }

   public double getCostToHereFromStart()
   {
      return costToHereFromStart;
   }

   public void setCostToHereFromStart(double costToHereFromStart)
   {
      this.costToHereFromStart = costToHereFromStart;
   }

   public double getEstimatedCostToGoal()
   {
      return estimatedCostToGoal;
   }

   public void setEstimatedCostToGoal(double estimatedCostToGoal)
   {
      this.estimatedCostToGoal = estimatedCostToGoal;
   }

   private final Vector3d tempPointA = new Vector3d();
   private final Vector3d tempPointB = new Vector3d();
   private final Vector3d tempRotationVectorA = new Vector3d();
   private final Vector3d tempRotationVectorB = new Vector3d();

   @Override
   public boolean equals(Object o)
   {
      if(!(o instanceof BipedalFootstepPlannerNode))
      {
         return false;
      }
      else
      {
         BipedalFootstepPlannerNode otherNode = (BipedalFootstepPlannerNode) o;

         if(getRobotSide() != otherNode.getRobotSide())
         {
            return false;
         }

         this.soleTransform.getTranslation(tempPointA);
         MathTools.roundToGivenPrecision(tempPointA, XY_DISTANCE_THRESHOLD_TO_CONSIDER_NODES_EQUAL);

         otherNode.soleTransform.getTranslation(tempPointB);
         MathTools.roundToGivenPrecision(tempPointB, XY_DISTANCE_THRESHOLD_TO_CONSIDER_NODES_EQUAL);

         this.soleTransform.getRotationEuler(tempRotationVectorA);
         double thisYaw = MathTools.roundToGivenPrecisionForAngle(tempRotationVectorA.getX(), YAW_ROTATION_THRESHOLD_TO_CONSIDER_NODES_EQUAL);

         otherNode.soleTransform.getRotationEuler(tempRotationVectorB);
         double otherYaw = MathTools.roundToGivenPrecisionForAngle(tempRotationVectorA.getX(), YAW_ROTATION_THRESHOLD_TO_CONSIDER_NODES_EQUAL);

         tempPointA.sub(tempPointB);
         tempPointA.setZ(0.0);

//         tempRotationVectorA.sub(tempRotationVectorB);
//         double yawDifference = AngleTools.computeAngleDifferenceMinusPiToPi(thisYaw, otherYaw);

         return (tempPointA.length() < 1e-10); //  && (Math.abs(yawDifference) < 1e-10);
      }
   }

   @Override
   public int hashCode()
   {
      this.soleTransform.getTranslation(tempPointA);
      MathTools.roundToGivenPrecision(tempPointA, XY_DISTANCE_THRESHOLD_TO_CONSIDER_NODES_EQUAL);

      this.soleTransform.getRotationEuler(tempRotationVectorA);
      MathTools.roundToGivenPrecisionForAngles(tempRotationVectorA, YAW_ROTATION_THRESHOLD_TO_CONSIDER_NODES_EQUAL);

      int result = getRobotSide().hashCode();
      result = 3 * result + (int) Math.round(tempPointA.getX() / XY_DISTANCE_THRESHOLD_TO_CONSIDER_NODES_EQUAL);
      result = 3 * result + (int) Math.round(tempPointA.getY() / XY_DISTANCE_THRESHOLD_TO_CONSIDER_NODES_EQUAL);
//      result = 3 * result + (int) Math.round(tempRotationVectorA.getX() / YAW_ROTATION_THRESHOLD_TO_CONSIDER_NODES_EQUAL);

      return result;
   }

   public static double getXyDistanceThresholdToConsiderNodesEqual()
   {
      return XY_DISTANCE_THRESHOLD_TO_CONSIDER_NODES_EQUAL;
   }

   public static double getYawRotationThresholdToConsiderNodesEqual()
   {
      return YAW_ROTATION_THRESHOLD_TO_CONSIDER_NODES_EQUAL;
   }

   public boolean epsilonEquals(BipedalFootstepPlannerNode nodeToCheck, double epsilon)
   {
      if (nodeToCheck.footstepSide != this.footstepSide) return false;
      if (!nodeToCheck.soleTransform.epsilonEquals(this.soleTransform, epsilon)) return false;

      return true;
   }
}