package us.ihmc.simulationconstructionset.physics.collision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import us.ihmc.simulationconstructionset.ExternalForcePoint;
import us.ihmc.simulationconstructionset.Link;
import us.ihmc.simulationconstructionset.Robot;
import us.ihmc.simulationconstructionset.physics.CollisionHandler;
import us.ihmc.simulationconstructionset.physics.CollisionShapeWithLink;
import us.ihmc.simulationconstructionset.physics.Contacts;
import us.ihmc.simulationconstructionset.physics.ScsCollisionDetector;

public class DefaultCollisionHandler implements CollisionHandler
{
   private final Random random;

   private final Vector3d normal = new Vector3d();
   private final Vector3d negative_normal = new Vector3d();

   private Point3d point1 = new Point3d();
   private Point3d point2 = new Point3d();

   private List<CollisionHandlerListener> listeners = new ArrayList<CollisionHandlerListener>();

   // Coefficent of restitution.
   private final double epsilon;

   // Coefficient of friction
   private final double mu;

   public DefaultCollisionHandler(double epsilon, double mu)
   {
      this(new Random(), epsilon, mu);
   }

   /**
    *
    * @param epsilon coefficent of restitution.
    * @param mu coefficient of friction
    * @param robot Robot model
    */
   public DefaultCollisionHandler(Random random, double epsilon, double mu)
   {
      this.random = random;
      this.epsilon = epsilon;
      this.mu = mu;
   }

   public void maintenanceBeforeCollisionDetection()
   {
      shapesInContactList.clear();
   }

   public void maintenanceAfterCollisionDetection()
   {
      Collections.shuffle(shapesInContactList, random);

      for (int i=0; i<shapesInContactList.size(); i++)
      {
         Contacts shapesInContact = shapesInContactList.get(i);

         //TODO: Get rid of Type cast here...
         CollisionShapeWithLink shape1 = (CollisionShapeWithLink) shapesInContact.getShapeA();
         CollisionShapeWithLink shape2 = (CollisionShapeWithLink) shapesInContact.getShapeB();
         handleLocal(shape1, shape2, shapesInContact);
      }
   }

   private final ArrayList<Contacts> shapesInContactList = new ArrayList<Contacts>();

   public void handle(Contacts contacts)
   {
      shapesInContactList.add(contacts);
//      handleLocal(shape1, shape2, contacts);
   }

   private final ArrayList<Integer> indices = new ArrayList<Integer>();

   private void handleLocal(CollisionShapeWithLink shape1, CollisionShapeWithLink shape2, Contacts contacts)
   {
      boolean shapeOneIsGround = shape1.isGround();
      boolean shapeTwoIsGround = shape2.isGround();
      if (shapeOneIsGround && shapeTwoIsGround) throw new RuntimeException("Both shapes are ground. Shouldn't be contacting!!");

      int numberOfContacts = contacts.getNumberOfContacts();
      indices.clear();

      for (int i=0; i<numberOfContacts; i++)
      {
         indices.add(i);
      }

      // TODO: Sims won't sim same way twice, but I don't think they do anyway...
      Collections.shuffle(indices);

      for (int j = 0; j < numberOfContacts; j++)
      {
         int i = indices.get(j);
         double distance = contacts.getDistance(i);

         if (distance > 0)
            continue;

         contacts.getWorldA(i, point1);
         contacts.getWorldB(i, point2);

         contacts.getWorldNormal(i, normal);

         if (!contacts.isNormalOnA())
         {
            normal.scale(-1.0);
         }

         // TODO handle the case where the object is embedded inside the object and the normal is invalid
         if (Double.isNaN(normal.getX()))
            throw new RuntimeException("Normal is invalid. Contains NaN!");

         negative_normal.set(normal);
         negative_normal.scale(-1.0);

         Link linkOne = shape1.getLink();
         Link linkTwo = shape2.getLink();

         ExternalForcePoint externalForcePointOne = linkOne.ef_collision;
         ExternalForcePoint externalForcePointTwo = linkTwo.ef_collision;

         // +++JEP: For now. Make more efficient later. Don't need an ef_point really...
         externalForcePointOne.setOffsetWorld(point1.getX(), point1.getY(), point1.getZ());    // Put the external force points in the right places.
         externalForcePointTwo.setOffsetWorld(point2.getX(), point2.getY(), point2.getZ());

         // Update the robot and its velocity:
         Robot robot1 = linkOne.getParentJoint().getRobot();
         Robot robot2 = linkTwo.getParentJoint().getRobot();

         robot1.updateVelocities();
         robot1.update();

         if (robot2 != robot1)
         {
            robot2.updateVelocities();
            robot2.update();
         }

         // Resolve the collision:
         Vector3d p_world = new Vector3d();

         // +++JEP: epsilon, mu hardcoded on construction right now. Need to change that!

         boolean collisionOccurred;


//         System.out.println("numberOfContacts = " + numberOfContacts);
//         System.out.println("normal = " + normal);
//         System.out.println("negative_normal = " + negative_normal);
//         System.out.println("point1 = " + point1);
//         System.out.println("point2 = " + point2);
//         System.out.println("externalForcePointOne = " + externalForcePointOne);
//         System.out.println("externalForcePointTwo = " + externalForcePointTwo);

         if (shapeTwoIsGround)
         {
//            System.out.println("shapeTwoIsGround");
            Vector3d velocityWorld = new Vector3d(0.0, 0.0, 0.0);
            collisionOccurred = externalForcePointOne.resolveCollision(velocityWorld , negative_normal, epsilon, mu, p_world);    // link1.epsilon, link1.mu, p_world);
         }
         else if (shapeOneIsGround)
         {
//            System.out.println("shapeOneIsGround");
            Vector3d velocityWorld = new Vector3d(0.0, 0.0, 0.0);
            collisionOccurred = externalForcePointTwo.resolveCollision(velocityWorld, normal, epsilon, mu, p_world);    // link1.epsilon, link1.mu, p_world);
         }
         else
         {
//            System.out.println("Two ef points");
            collisionOccurred = externalForcePointOne.resolveCollision(externalForcePointTwo, negative_normal, epsilon, mu, p_world);    // link1.epsilon, link1.mu, p_world);
         }

         if (collisionOccurred)
         {
            for (CollisionHandlerListener listener : listeners)
            {
//               System.out.println("collision occured. Visualizing it...");
//               System.out.println("externalForcePointOne = " + externalForcePointOne);
//               System.out.println("externalForcePointTwo = " + externalForcePointTwo);

               listener.collision(shape1, shape2, externalForcePointOne, externalForcePointTwo, null, null);
            }
         }

      }
   }

   public void addListener(CollisionHandlerListener listener)
   {
      listeners.add(listener);
   }

   @Override
   public void handleCollisions(CollisionDetectionResult results)
   {
      this.maintenanceBeforeCollisionDetection();

      for (int i=0; i<results.getNumberOfCollisions(); i++)
      {
         Contacts collision = results.getCollision(i);
         handle(collision);
      }

      this.maintenanceAfterCollisionDetection();
   }
}
