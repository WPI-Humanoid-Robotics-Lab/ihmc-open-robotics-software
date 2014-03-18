package us.ihmc.atlas;

import javax.media.j3d.Transform3D;
import javax.vecmath.Vector3d;

import us.ihmc.darpaRoboticsChallenge.drcRobot.DRCRobotPhysicalProperties;
import us.ihmc.utilities.math.geometry.TransformTools;

public class AtlasPhysicalProperties extends DRCRobotPhysicalProperties
{
   public static final double ankleHeight = 0.084;
   public static final double pelvisToFoot = 0.887;
   public static final Transform3D ankle_to_sole_frame_tranform = TransformTools.createTranslationTransform(new Vector3d(0.0, 0.0, -ankleHeight));;
   public static final double  foot_width = 0.12;   // 0.08;   //0.124887;
   public static final double  toe_width = 0.095;  //0.07;   //0.05;   //
   public static final double  foot_length = 0.255;
   public static final double  foot_back = 0.09; // 0.06;   //0.082;    // 0.07;
   public static final double  foot_start_toetaper_from_back = 0.195;
   public static final double  foot_forward = foot_length - foot_back;   // 0.16;   //0.178;    // 0.18;
   
   @Override
   public double getAnkleHeight()
   {
      return ankleHeight;
   }

}
