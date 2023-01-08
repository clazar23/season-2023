package frc.robot.subsystems;

import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
// import frc.robot.RobotMap;
import static frc.robot.RobotMap.CameraMap.*;
import org.photonvision.PhotonCamera;
import org.photonvision.PhotonUtils;
import edu.wpi.first.apriltag.AprilTag;
import edu.wpi.first.apriltag.AprilTagFieldLayout;
import edu.wpi.first.math.Pair;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Pose3d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Transform3d;
import edu.wpi.first.wpilibj.Timer;
import org.photonvision.PhotonCamera;
import org.photonvision.RobotPoseEstimator;
import org.photonvision.RobotPoseEstimator.PoseStrategy;
import org.photonvision.targeting.PhotonTrackedTarget;

public class Vision extends SubsystemBase {

  private static Vision instance;

  public static Vision getInstance() {
    if (instance == null) instance = new Vision();
    return instance;
  }

  // Camera
  private PhotonCamera vision;

  // Target information
  private PhotonTrackedTarget latestTarget;

  public Vision() {
    vision = new PhotonCamera(COMPUTER_VISION);
  }

  private void updateResult() {
    if (vision.getLatestResult().hasTargets())
      latestTarget = vision.getLatestResult().getBestTarget();
  }


  /* 2D Alignment
   * There is no pose estimation; therefore, you can not program
   * the drivetrain to be directly in line with the face of the 
   * april tag (only have it look in the direction of the april tag)
  */
  public double getAngle() {
    return latestTarget.getYaw();
  }

  public double getRange() {
    return PhotonUtils.calculateDistanceToTargetMeters(
      CAMERA_HEIGHT_METRES, 
      TARGET_HEIGHT_METRES, 
      CAMERA_PITCH_RADIANS, 
      Units.degreesToRadians(latestTarget.getPitch()));
  }


  /* 3D Alignment (requires homography)
   * Uses pose estimation; therefore one can identify their 
   * position on a field using a single april tag
  */
  


  @Override
  public void periodic() {
    updateResult();
  }
}