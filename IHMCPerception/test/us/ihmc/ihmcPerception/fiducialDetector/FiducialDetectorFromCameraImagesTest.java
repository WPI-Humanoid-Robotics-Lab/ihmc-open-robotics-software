package us.ihmc.ihmcPerception.fiducialDetector;

import java.awt.image.BufferedImage;

import javax.vecmath.Point3d;
import javax.vecmath.Quat4d;
import javax.vecmath.Vector3d;

import org.junit.Test;

import boofcv.struct.calib.IntrinsicParameters;
import us.ihmc.communication.net.AtomicSettableTimestampProvider;
import us.ihmc.graphics3DAdapter.camera.CameraConfiguration;
import us.ihmc.graphics3DAdapter.camera.RenderedSceneHandler;
import us.ihmc.graphics3DDescription.Graphics3DObject;
import us.ihmc.graphics3DDescription.appearance.AppearanceDefinition;
import us.ihmc.graphics3DDescription.appearance.YoAppearance;
import us.ihmc.graphics3DDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.robotics.dataStructures.listener.VariableChangedListener;
import us.ihmc.robotics.dataStructures.variable.BooleanYoVariable;
import us.ihmc.robotics.dataStructures.variable.DoubleYoVariable;
import us.ihmc.robotics.dataStructures.variable.YoVariable;
import us.ihmc.robotics.geometry.RigidBodyTransform;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.testing.YoVariableTestGoal;
import us.ihmc.simulationconstructionset.CameraMount;
import us.ihmc.simulationconstructionset.FloatingJoint;
import us.ihmc.simulationconstructionset.Link;
import us.ihmc.simulationconstructionset.Robot;
import us.ihmc.simulationconstructionset.SimulationConstructionSet;
import us.ihmc.simulationconstructionset.bambooTools.SimulationTestingParameters;
import us.ihmc.simulationconstructionset.util.environments.FiducialsFlatGroundEnvironment.Fiducial;
import us.ihmc.simulationconstructionset.util.simulationRunner.GoalOrientedTestConductor;
import us.ihmc.tools.TimestampProvider;
import us.ihmc.tools.continuousIntegration.ContinuousIntegrationAnnotations.ContinuousIntegrationTest;
import us.ihmc.tools.continuousIntegration.ContinuousIntegrationTools;
import us.ihmc.tools.thread.ThreadTools;

public class FiducialDetectorFromCameraImagesTest
{
   private static final SimulationTestingParameters simulationTestingParameters = SimulationTestingParameters.createFromEnvironmentVariables();

   @ContinuousIntegrationTest(estimatedDuration = 5.0)
   @Test//(timeout = 30000)
   public void testUsingSimulationConstructionSet()
   {
      double fieldOfView = 0.81;

      final Robot simpleRobotWithCamera = createCameraRobot(fieldOfView);
      final QRCodeRobot simpleBoxRobotWithQRCode = new QRCodeRobot();

      YoGraphicsListRegistry yoGraphicsListRegistry = new YoGraphicsListRegistry();

      RigidBodyTransform transformFromReportedToFiducialFrame = new RigidBodyTransform();
      transformFromReportedToFiducialFrame.setRotationEulerAndZeroTranslation(0.0, 0.0, Math.PI/2.0);

      final FiducialDetectorFromCameraImages detector = new FiducialDetectorFromCameraImages(transformFromReportedToFiducialFrame, simpleRobotWithCamera.getRobotsYoVariableRegistry(), yoGraphicsListRegistry);
      detector.setTargetIDToLocate(50L);

      detector.setFieldOfView(fieldOfView, fieldOfView);

      SimulationConstructionSet scsForDetecting = new SimulationConstructionSet(new Robot[] { simpleRobotWithCamera, simpleBoxRobotWithQRCode });
      scsForDetecting.addYoGraphicsListRegistry(yoGraphicsListRegistry);

      CameraConfiguration cameraConfiguration = new CameraConfiguration("cameraMount");
      cameraConfiguration.setCameraMount("cameraMount");
//      cameraConfiguration.setCameraFieldOfView(fieldOfView);
      scsForDetecting.setupCamera(cameraConfiguration);

      scsForDetecting.setDT(0.001, 10);
      scsForDetecting.setSimulateNoFasterThanRealTime(true);

      int width = 800;
      int height = 800;
      RenderedSceneHandler videoDataServer = new RenderedSceneHandler()
      {
         @Override
         public void updateImage(RobotSide robotSide, BufferedImage bufferedImage, long timeStamp, Point3d cameraPosition, Quat4d cameraOrientation, IntrinsicParameters intrinsicParameters)
         {
            FloatingJoint cameraJoint = (FloatingJoint) simpleRobotWithCamera.getRootJoints().get(0);

            Point3d cameraPositionInWorld = new Point3d();
            Quat4d cameraOrientationInWorldXForward = new Quat4d();

            cameraJoint.getPosition(cameraPositionInWorld);
            cameraJoint.getRotationToWorld(cameraOrientationInWorldXForward);

//            System.out.println("Received image.");
//            System.out.println("intrinsicParameters.width = " + intrinsicParameters.width);
//            System.out.println("intrinsicParameters.height = " + intrinsicParameters.height);
//            System.out.println("intrinsicParameters.fx = " + intrinsicParameters.fx);
//            System.out.println("intrinsicParameters.fy = " + intrinsicParameters.fy);
//            System.out.println("intrinsicParameters.skew = " + intrinsicParameters.skew);
//            System.out.println("intrinsicParameters.cx = " + intrinsicParameters.cx);
//            System.out.println("intrinsicParameters.cy = " + intrinsicParameters.cy);
//            System.out.println("intrinsicParameters.t1 = " + intrinsicParameters.t1);
//            System.out.println("intrinsicParameters.t2 = " + intrinsicParameters.t2);
//            System.out.println("intrinsicParameters.radial.length = " + intrinsicParameters.radial.length);
//            System.out.println("intrinsicParameters.radial[0] = " + intrinsicParameters.radial[0]);
//            System.out.println("intrinsicParameters.radial[1] = " + intrinsicParameters.radial[1]);

            detector.setNewVideoPacket(bufferedImage, cameraPositionInWorld, cameraOrientationInWorldXForward);
         }

         @Override
         public boolean isReadyForNewData()
         {
            return true;
         }

         @Override
         public void close()
         {
         }
      };

      TimestampProvider timestampProvider = new AtomicSettableTimestampProvider();
      int framesPerSecond = 10;

      scsForDetecting.startStreamingVideoData(cameraConfiguration, width, height, videoDataServer, timestampProvider, framesPerSecond);

      GoalOrientedTestConductor testConductor = new GoalOrientedTestConductor(scsForDetecting, simulationTestingParameters);


      BooleanYoVariable fiducialTargetIDHasBeenLocated = (BooleanYoVariable) scsForDetecting.getVariable("fiducialTargetIDHasBeenLocated");

      DoubleYoVariable fiducialReportedPoseWorldFrameX = (DoubleYoVariable) scsForDetecting.getVariable("fiducialReportedPoseWorldFrameX");
      DoubleYoVariable fiducialReportedPoseWorldFrameY = (DoubleYoVariable) scsForDetecting.getVariable("fiducialReportedPoseWorldFrameY");
      DoubleYoVariable fiducialReportedPoseWorldFrameZ = (DoubleYoVariable) scsForDetecting.getVariable("fiducialReportedPoseWorldFrameZ");

      DoubleYoVariable fiducialReportedPoseWorldFrameQS = (DoubleYoVariable) scsForDetecting.getVariable("fiducialReportedPoseWorldFrameQS");
      DoubleYoVariable fiducialReportedPoseWorldFrameQX = (DoubleYoVariable) scsForDetecting.getVariable("fiducialReportedPoseWorldFrameQX");
      DoubleYoVariable fiducialReportedPoseWorldFrameQY = (DoubleYoVariable) scsForDetecting.getVariable("fiducialReportedPoseWorldFrameQY");
      DoubleYoVariable fiducialReportedPoseWorldFrameQZ = (DoubleYoVariable) scsForDetecting.getVariable("fiducialReportedPoseWorldFrameQZ");


      DoubleYoVariable q_qrCode_x = (DoubleYoVariable) scsForDetecting.getVariable("q_qrCode_x");
      DoubleYoVariable q_qrCode_y = (DoubleYoVariable) scsForDetecting.getVariable("q_qrCode_y");
      DoubleYoVariable q_qrCode_z = (DoubleYoVariable) scsForDetecting.getVariable("q_qrCode_z");

      DoubleYoVariable q_qrCode_qs = (DoubleYoVariable) scsForDetecting.getVariable("q_qrCode_qs");
      DoubleYoVariable q_qrCode_qx = (DoubleYoVariable) scsForDetecting.getVariable("q_qrCode_qx");
      DoubleYoVariable q_qrCode_qy = (DoubleYoVariable) scsForDetecting.getVariable("q_qrCode_qy");
      DoubleYoVariable q_qrCode_qz = (DoubleYoVariable) scsForDetecting.getVariable("q_qrCode_qz");

      final DoubleYoVariable time = simpleRobotWithCamera.getYoTime();

      time.addVariableChangedListener(new VariableChangedListener()
      {
         @Override
         public void variableChanged(YoVariable<?> v)
         {
            double t = time.getDoubleValue();
            double amp = 0.1;
            double freqX = 1.0;
            double freqY = 1.27;
            double freqZ = 1.92;

            double wX = amp * Math.sin(2.0 * Math.PI * freqX * t);
            double wY = amp * Math.sin(2.0 * Math.PI * freqY * t);
            double wZ = amp * Math.sin(2.0 * Math.PI * freqZ * t);

            Vector3d angularVelocityInBody = new Vector3d(wX, wY, wZ);
            simpleBoxRobotWithQRCode.setAngularVelocity(angularVelocityInBody );
         }
      });

      testConductor.addTerminalGoal(YoVariableTestGoal.doubleGreaterThan(time, 3.0));

      testConductor.addSustainGoal(YoVariableTestGoal.booleanEquals(fiducialTargetIDHasBeenLocated, true));

      double okTrackingDeltaPosition = 0.15;
      testConductor.addSustainGoal(YoVariableTestGoal.variablesEqual(fiducialReportedPoseWorldFrameX, q_qrCode_x, okTrackingDeltaPosition));
      testConductor.addSustainGoal(YoVariableTestGoal.variablesEqual(fiducialReportedPoseWorldFrameY, q_qrCode_y, okTrackingDeltaPosition));
      testConductor.addSustainGoal(YoVariableTestGoal.variablesEqual(fiducialReportedPoseWorldFrameZ, q_qrCode_z, okTrackingDeltaPosition));

      double okTrackingDeltaQuaternion = 0.1;
      testConductor.addSustainGoal(YoVariableTestGoal.variablesEqual(fiducialReportedPoseWorldFrameQS, q_qrCode_qs, okTrackingDeltaQuaternion));
      testConductor.addSustainGoal(YoVariableTestGoal.variablesEqual(fiducialReportedPoseWorldFrameQX, q_qrCode_qx, okTrackingDeltaQuaternion));
      testConductor.addSustainGoal(YoVariableTestGoal.variablesEqual(fiducialReportedPoseWorldFrameQY, q_qrCode_qy, okTrackingDeltaQuaternion));
      testConductor.addSustainGoal(YoVariableTestGoal.variablesEqual(fiducialReportedPoseWorldFrameQZ, q_qrCode_qz, okTrackingDeltaQuaternion));

      testConductor.simulate();

      if (!ContinuousIntegrationTools.isRunningOnContinuousIntegrationServer())
      {
         ThreadTools.sleepForever();
      }

      testConductor.concludeTesting();

   }

   private Robot createCameraRobot(double fieldOfView)
   {
      final Robot simpleRobotWithCamera = new Robot("SimpleRobotWithCamera");
      FloatingJoint cameraJoint = new FloatingJoint("camera", "camera", new Vector3d(), simpleRobotWithCamera);
      Link cameraLink = new Link("camera");
      cameraLink.setMassAndRadiiOfGyration(1.0, 0.1, 0.1, 0.1);
      Graphics3DObject cameraLinkGraphics = new Graphics3DObject();
      cameraLinkGraphics.translate(-0.251, 0.0, 0.0);
      cameraLinkGraphics.addCoordinateSystem(0.25);
      cameraLink.setLinkGraphics(cameraLinkGraphics);
      cameraJoint.setLink(cameraLink);

      RigidBodyTransform cameraOffsetTransform = new RigidBodyTransform();

      double clipDistanceNear = 0.01;
      double clipDistanceFar = 10.0;

      CameraMount cameraMount = new CameraMount("cameraMount", cameraOffsetTransform, fieldOfView, clipDistanceNear, clipDistanceFar, simpleRobotWithCamera);
      cameraJoint.addCameraMount(cameraMount);

      simpleRobotWithCamera.addRootJoint(cameraJoint);
      simpleRobotWithCamera.setGravity(0.0);
      cameraJoint.setPosition(0.0, 0.0, 2.0);
      return simpleRobotWithCamera;
   }

   private class QRCodeRobot extends Robot
   {
      private final FloatingJoint qrCodeJoint;

      public QRCodeRobot()
      {
         super("SimpleBoxRobotWithQRCode");

         qrCodeJoint = new FloatingJoint("qrCode", "qrCode", new Vector3d(), this);
         Link qrCodeLink = new Link("qrCode");
         qrCodeLink.setMassAndRadiiOfGyration(1.0, 0.1, 0.1, 0.1);
         Graphics3DObject qrCodeLinkGraphics = new Graphics3DObject();
         //      qrCodeLinkGraphics.addCoordinateSystem(2.0);
         double cubeLength = 1.0;
         qrCodeLinkGraphics.translate(0.0, 0.0, -0.99*cubeLength);
         AppearanceDefinition cubeAppearance = YoAppearance.Texture(Fiducial.FIDUCIAL50.getPathString());
         qrCodeLinkGraphics.addCube(cubeLength * 0.98, cubeLength * 1.01, cubeLength * 0.98, YoAppearance.Yellow());

         boolean[] textureFaces = new boolean[]{true, true, false, false, true, true};
         qrCodeLinkGraphics.translate(0.0, 0.0, -0.01 * cubeLength);
         qrCodeLinkGraphics.addCube(cubeLength, cubeLength, cubeLength, cubeAppearance, textureFaces);

         qrCodeLink.setLinkGraphics(qrCodeLinkGraphics);
         qrCodeJoint.setLink(qrCodeLink);
         addRootJoint(qrCodeJoint);
         setGravity(0.0);

         qrCodeJoint.setPosition(6.0, 0.0, 2.0);
         qrCodeJoint.setYawPitchRoll(0.0, -Math.PI/2.0, 0.0);
         //      qrCodeJoint.setAngularVelocityInBody(new Vector3d(0.0, 0.0, 1.0));
      }

      public void setAngularVelocity(Vector3d angularVelocityInBody)
      {
         qrCodeJoint.setAngularVelocityInBody(angularVelocityInBody);
      }
   }
}
