// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import com.ctre.phoenix.sensors.WPI_Pigeon2;
import com.pathplanner.lib.PathPlanner;
import com.pathplanner.lib.PathPlannerTrajectory;
import com.pathplanner.lib.commands.PPSwerveControllerCommand;

import edu.wpi.first.math.Pair;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveDriveOdometry;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.math.trajectory.Trajectory;
import edu.wpi.first.util.sendable.Sendable;
import edu.wpi.first.math.estimator.SwerveDrivePoseEstimator;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.FunctionalCommand;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.RepeatCommand;
import edu.wpi.first.wpilibj2.command.RunCommand;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.RobotMap;
import frc.robot.RobotMap.DriveMap;
import frc.robot.util.SwerveModule;
import pixy2api.Pixy2CCC.Block;
import frc.robot.subsystems.Vision;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;



public class Swerve extends SubsystemBase {
  private static Swerve instance;

  public static Swerve getInstance() {
    if (instance == null)
      instance = new Swerve();
    return instance;
  }

  private SwerveDriveOdometry odometry;
  private SwerveModule[] modules;
  private WPI_Pigeon2 gyro;

  //Camera
  Vision vision;
  private PixyCam pixyCam;
  PIDController speedController = new PIDController(0.0001, 0, 0);

  //Swerve Pose Estimator
  private SwerveDrivePoseEstimator poseEstimator;

  private Swerve() {
    gyro = new WPI_Pigeon2(DriveMap.PIGEON_ID);
    gyro.configFactoryDefault();
    zeroGyro();

    // vision = Vision.getInstance();
    // pixyCam = PixyCam.getInstance();
    
    modules = new SwerveModule[] {
        new SwerveModule(0, DriveMap.FrontLeft.CONSTANTS),
        new SwerveModule(1, DriveMap.FrontRight.CONSTANTS),
        new SwerveModule(2, DriveMap.BackLeft.CONSTANTS),
        new SwerveModule(3, DriveMap.BackRight.CONSTANTS)
    };

    odometry = new SwerveDriveOdometry(DriveMap.KINEMATICS, getYaw(), getModulePositions());
    poseEstimator = new SwerveDrivePoseEstimator(DriveMap.KINEMATICS, getYaw(), getModulePositions(), getPose());
  }

  public void drive(ChassisSpeeds speeds, boolean isOpenLoop) {
    SwerveModuleState[] swerveModuleStates = DriveMap.KINEMATICS.toSwerveModuleStates(speeds);
    SwerveDriveKinematics.desaturateWheelSpeeds(swerveModuleStates, DriveMap.MAX_VELOCITY);

    

    for (SwerveModule mod : modules) {
      mod.setDesiredState(swerveModuleStates[mod.moduleNumber], isOpenLoop);
    }
  }

  public Command driveCommand(Supplier<ChassisSpeeds> chassisSpeeds) {
    return new RepeatCommand(new RunCommand(() -> this.drive(chassisSpeeds.get(), true), this));
  }

  private List<Boolean> lastTenFrames = new ArrayList<>();

  public Command AlignWithGameObject() {
    PIDController speedController2 = new PIDController(0.0001, 0, 0);
    speedController2.setTolerance(RobotMap.DriveMap.PIXYCAM_PID_POSITION_TOLERANCE, RobotMap.DriveMap.PIXYCAM_PID_VELOCITY_TOLERANCE);
    return new FunctionalCommand(
        () -> {
          pixyCam.setObjectIndex(-1);
          var cones = pixyCam.getBlocksOfType(2);
          var cubes = pixyCam.getBlocksOfType(1);
          Block biggestCone = null, biggestCube = null;
          

          if(!cones.isEmpty()) {
            biggestCone = pixyCam.getLargestBlock(cones);
            pixyCam.setBiggestObject(biggestCone);
          }

          if(!cubes.isEmpty()) {
            biggestCube = pixyCam.getLargestBlock(cubes);
            pixyCam.setBiggestObject(biggestCube);
          }
          
          if(!cubes.isEmpty() && !cones.isEmpty()) {
            if ((biggestCone.getWidth() * biggestCone.getHeight()) >= (biggestCube.getWidth() * biggestCube.getHeight())) {
              pixyCam.setBiggestObject(biggestCone);
            } else {
              pixyCam.setBiggestObject(biggestCube);
            }
          }
          if(cubes.isEmpty() && cones.isEmpty()) return;
          pixyCam.setObjectIndex(pixyCam.getBiggestObject().getIndex());
        },
        () -> {
          
          if(pixyCam.getObjectIndex() != -1){
            pixyCam.setBiggestObject(pixyCam.getBlockByIndex(pixyCam.getObjectIndex()));
          } else {
            return;
          }
          //SmartDashboard.putData("PixyCam PID Controller", speedController);
          //SmartDashboard.putNumber("error", speedController.getPositionError());
          
          ChassisSpeeds newSpeed = 
              new ChassisSpeeds(0.0,
                                0.0,
                                speedController2.calculate(pixyCam.getBiggestObject().getX(), RobotMap.DriveMap.PIXYCAM_RESOLUTION/2));

          drive(newSpeed, false);
        },
        interrupted -> {
          System.out.println("End");
          System.out.println(interrupted);
          ChassisSpeeds endSpeed = new ChassisSpeeds(0.0, 0.0, 0.0);
          drive(endSpeed, false);
        },
        () -> {
          if(pixyCam.getObjectIndex() == -1){
            return true;
          }
          if(speedController2.atSetpoint()){
            System.out.println("At Setpoint");
            return true;
          }
          boolean anythingDetected = !pixyCam.getBlocksOfType(1).isEmpty() || !pixyCam.getBlocksOfType(2).isEmpty();
          lastTenFrames.add(anythingDetected);
          if(lastTenFrames.size() > 10) lastTenFrames.remove(0);
         
          boolean noBlocks = true;
          for(boolean detected: lastTenFrames) {
            if(detected) noBlocks = false;
          }
          if(noBlocks) return true;

          
          return false;
        },
        this, pixyCam

  );

  }
  public void camData(){
    speedController.setTolerance(RobotMap.DriveMap.PIXYCAM_PID_POSITION_TOLERANCE, RobotMap.DriveMap.PIXYCAM_PID_VELOCITY_TOLERANCE);
    var cones = pixyCam.getBlocksOfType(2);
    var cubes = pixyCam.getBlocksOfType(1);
    Block biggestCone = null, biggestCube = null;

    if(!cones.isEmpty()) {
      biggestCone = pixyCam.getLargestBlock(cones);
      pixyCam.setBiggestObject(biggestCone);
    }

    if(!cubes.isEmpty()) {
      biggestCube = pixyCam.getLargestBlock(cubes);
      pixyCam.setBiggestObject(biggestCube);
    }
    
    if(!cubes.isEmpty() && !cones.isEmpty()) {
      if ((biggestCone.getWidth() * biggestCone.getHeight()) >= (biggestCube.getWidth() * biggestCube.getHeight())) {
        pixyCam.setBiggestObject(biggestCone);
      } else {
        pixyCam.setBiggestObject(biggestCube);
      }
    }
    if(cubes.isEmpty() && cones.isEmpty()) return;
    var angularSpeed = speedController.calculate(pixyCam.getBiggestObject().getX(), RobotMap.DriveMap.PIXYCAM_RESOLUTION/2);
    SmartDashboard.putNumber("Angular speed", angularSpeed);
    SmartDashboard.putData("PixyCam PID Controller", speedController);
    SmartDashboard.putNumber("error", speedController.getPositionError());
    SmartDashboard.putNumber("PixyCam X Coord", pixyCam.getBiggestObject().getX());

  }
  /* Used by SwerveControllerCommand in Auto */
  public void setModuleStates(SwerveModuleState[] desiredStates) {
    SwerveDriveKinematics.desaturateWheelSpeeds(desiredStates, DriveMap.MAX_VELOCITY);

    for (SwerveModule mod : modules) {
      mod.setDesiredState(desiredStates[mod.moduleNumber], false);
    }
  }

  public Pose2d getPose() {
    return odometry.getPoseMeters();
  }

  public void resetOdometry(Pose2d pose) {
    odometry.resetPosition(getYaw(), getModulePositions(), pose);
  }

  public SwerveModuleState[] getModuleStates() {
    SwerveModuleState[] states = new SwerveModuleState[4];
    for (SwerveModule mod : modules) {
      states[mod.moduleNumber] = mod.getState();
    }

    return states;
  }

  public SwerveModulePosition[] getModulePositions() {
    SwerveModulePosition[] positions = new SwerveModulePosition[4];
    for (SwerveModule mod : modules) {
      positions[mod.moduleNumber] = mod.getPosition();
    }
    return positions;
  }

  public void zeroGyro() {
    gyro.setYaw(0);
  }

  public Rotation2d getYaw() {
    return (DriveMap.INVERT_GYRO)
        ? Rotation2d.fromDegrees(360 - gyro.getYaw())
        : Rotation2d.fromDegrees(gyro.getYaw());
  }

  public SequentialCommandGroup followTrajectoryCommand(String path, boolean isFirstPath) {
    return followTrajectoryCommand(path, new HashMap<>(), isFirstPath);
  }

  public SequentialCommandGroup followTrajectoryCommand(String path, HashMap<String, Command> eventMap, boolean isFirstPath) {
    System.out.println(path);
    // return new SequentialCommandGroup();
    PathPlannerTrajectory traj = PathPlanner.loadPath(path, 0.5, 0.1);
    // // System.out.println(traj.)P

    // Create PIDControllers for each movement (and set default values)
    PIDController xPID = new PIDController(5.0, 0.0, 0.0);
    PIDController yPID = new PIDController(5.0, 0.0, 0.0);
    PIDController thetaPID = new PIDController(1.0, 0.0, 0.0);

    // Create PID tuning widgets in Glass (not for use in competition)
    SmartDashboard.putData("x-input PID Controller", xPID);
    SmartDashboard.putData("y-input PID Controller", yPID);
    SmartDashboard.putData("rot PID Controller", thetaPID);

    return new SequentialCommandGroup(
        new InstantCommand(
            () -> {
              // Reset odometry for the first path you run during auto
              if (isFirstPath) {
                odometry.resetPosition(
                    getYaw(), getModulePositions(), traj.getInitialHolonomicPose());
              }
              System.out.println("we be reseting");
            }),
        new PPSwerveControllerCommand(
            traj, this::getPose, xPID, yPID, thetaPID, speeds -> drive(speeds, true), this));//KEEP IT OPEN LOOP
  }

  
  public void updateCameraOdometry() {
    poseEstimator.update(gyro.getRotation2d(), getModulePositions());
    Pair<Pose2d, Double> result = vision.getEstimatedGlobalPose(poseEstimator.getEstimatedPosition());
    
    SmartDashboard.putNumber("Relative Pose X", result.getFirst().getX());
    SmartDashboard.putNumber("Relative Pose Y", result.getFirst().getY());

    var camPose = result.getFirst();
    var camPoseObsTime = result.getSecond();
    if (camPose != null) {
      poseEstimator.addVisionMeasurement(camPose, camPoseObsTime);
    }

  } 

  public Pose2d getCameraPosition() { //In here because poseEstimator is a swerveDrivePoseEstimator
    return poseEstimator.getEstimatedPosition();
  }

  @Override
  public void periodic() {
    odometry.update(getYaw(), getModulePositions());
    updateCameraOdometry();

    for (SwerveModule mod : modules) {
      SmartDashboard.putNumber(
          "Mod " + mod.moduleNumber + " Cancoder", mod.getCanCoder().getDegrees());
      SmartDashboard.putNumber(
          "Mod " + mod.moduleNumber + " Integrated", mod.getPosition().angle.getDegrees());
      SmartDashboard.putNumber(
          "Mod " + mod.moduleNumber + " Velocity", mod.getState().speedMetersPerSecond);
    }
    //camData();
  }
}
