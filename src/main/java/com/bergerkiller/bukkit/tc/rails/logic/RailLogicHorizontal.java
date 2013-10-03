package com.bergerkiller.bukkit.tc.rails.logic;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Horizontal rail logic that does not operate on the vertical motion and position
 */
public class RailLogicHorizontal extends RailLogic {
	private static final RailLogicHorizontal[] values = new RailLogicHorizontal[8];
	static {
		for (int i = 0; i < 8; i++) {
			values[i] = new RailLogicHorizontal(FaceUtil.notchToFace(i));
		}
	}

	private final double dx, dz;
	private final double startX, startZ;
	private final BlockFace[] faces;

	protected RailLogicHorizontal(BlockFace direction) {
		super(direction);
		// Fix north/west, they are non-existent
		direction = FaceUtil.toRailsDirection(direction);
		// Faces and direction
		if (this.curved) {
			this.dx = 0.5 * direction.getModX();
			this.dz = -0.5 * direction.getModZ();
			// Invert direction, because it is wrong otherwise
			direction = direction.getOppositeFace();
		} else {
			this.dx = direction.getModX();
			this.dz = direction.getModZ();
		}
		// Start offset and direction faces
		this.faces = FaceUtil.getFaces(direction);
		final double startFactor = MathUtil.invert(0.5, !this.curved);
		this.startX = startFactor * faces[0].getModX();
		this.startZ = startFactor * faces[0].getModZ();
		// Invert north and south (is for some reason needed)
		for (int i = 0; i < this.faces.length; i++) {
			if (this.faces[i] == BlockFace.NORTH || this.faces[i] == BlockFace.SOUTH) {
				this.faces[i] = this.faces[i].getOppositeFace();
			}
		}
	}

	@Override
	public Vector getFixedPosition(CommonMinecart<?> entity, double x, double y, double z, IntVector3 railPos) {
		double newLocX = railPos.midX() + this.startX;
		double newLocY = railPos.midY();
		double newLocZ = railPos.midZ() + this.startZ;
		if (this.alongZ) {
			// Moving along the X-axis
			newLocZ += this.dz * (entity.loc.getZ() - railPos.z);
		} else if (this.alongX) {
			// Moving along the Z-axis
			newLocX += this.dx * (entity.loc.getX() - railPos.x);
		} else {
			// Curve
			double factor = 2.0 * (this.dx * (entity.loc.getX() - newLocX) + this.dz * (entity.loc.getZ() - newLocZ));
			newLocX += factor * this.dx;
			newLocZ += factor * this.dz;
		}
		// Calculate the Y-position
		return new Vector(newLocX, newLocY, newLocZ);
	}

	@Override
	public BlockFace getMovementDirection(MinecartMember<?> member, Vector movement) {
		final BlockFace raildirection = this.getDirection();
		final boolean isHorizontalMovement = Math.abs(movement.getX()) >= 0.0001 || Math.abs(movement.getZ()) >= 0.0001;
		BlockFace direction;

		if (this.isSloped()) {
			// Sloped rail logic
			if (isHorizontalMovement) {
				// Deal with minecarts moving on straight slopes
				float moveYaw = MathUtil.getLookAtYaw(movement);
				float diff1 = MathUtil.getAngleDifference(moveYaw, FaceUtil.faceToYaw(raildirection));
				float diff2 = MathUtil.getAngleDifference(moveYaw, FaceUtil.faceToYaw(raildirection.getOppositeFace()));
				// Compare with the previous direction to sort out equality problems
				if (diff1 == diff2) {
					diff1 = FaceUtil.getFaceYawDifference(member.getDirectionFrom(), raildirection);
					diff2 = FaceUtil.getFaceYawDifference(member.getDirectionFrom(), raildirection.getOppositeFace());
				}
				// Use the opposite direction if needed
				if (diff1 > diff2) {
					direction = raildirection.getOppositeFace();
				} else {
					direction = raildirection;
				}
			} else {
				// Deal with vertically moving or standing still minecarts on slopes
				if (Math.abs(movement.getY()) > 0.0001) {
					// Going from vertical to a slope
					if (movement.getY() > 0.0) {
						direction = raildirection;
					} else {
						direction = raildirection.getOppositeFace();
					}
				} else {
					// Gravity sends it down the slope at some point
					direction = raildirection.getOppositeFace();
				}
			}
		} else if (this.curved) {
			// Curved rail logic
			BlockFace movementDir = FaceUtil.getDirection(movement);
			BlockFace[] possibleDirections = FaceUtil.getFaces(raildirection.getOppositeFace());
			if (FaceUtil.isSubCardinal(movementDir)) {
				direction = movementDir;
			} else {
				// Evaluate ingoing/outgoing faces with direction
				BlockFace directionTo;
				if (possibleDirections[0] == movementDir) {
					// Move towards 0
					directionTo = possibleDirections[0];
				} else if (possibleDirections[1] == movementDir) {
					// Move towards 1
					directionTo = possibleDirections[1];
				} else if (possibleDirections[0].getOppositeFace() == movementDir) {
					// Move into 0, towards 1
					directionTo = possibleDirections[1];
				} else if (possibleDirections[1].getOppositeFace() == movementDir) {
					// Move into 1, towards 0
					directionTo = possibleDirections[0];
				} else {
					// Unknown, fallback
					directionTo = movementDir;
				}
				// Calculate the movement direction from the 'to' direction
				direction = FaceUtil.getRailsCartDirection(raildirection);
				if (!LogicUtil.contains(directionTo, FaceUtil.getFaces(direction))) {
					direction = direction.getOppositeFace();
				}
			}
		} else {
			// Straight rail logic
			// Find the right direction by tracking two 180-degree hemispheres
			float angleSide1 = FaceUtil.faceToYaw(raildirection);
			float angleSide2 = FaceUtil.faceToYaw(raildirection.getOppositeFace());
			float movAngle = MathUtil.getLookAtYaw(movement);
			if (MathUtil.getAngleDifference(angleSide1, movAngle) < MathUtil.getAngleDifference(angleSide2, movAngle)) {
				direction = raildirection;
			} else {
				direction = raildirection.getOppositeFace();
			}
		}
		return direction;
	}

	@Override
	public void onPreMove(MinecartMember<?> member) {
		final CommonMinecart<?> entity = member.getEntity();
		// Apply velocity modifiers
		final boolean invert;
		if (this.curved) {
			// Invert only if heading towards the exit-direction of the curve
			BlockFace from = member.getDirectionTo();
			invert = from == this.faces[0] || from == this.faces[1];
		} else {
			// Invert only if the direction is inverted relative to cart velocity
			invert = (entity.vel.getX() * this.dx + entity.vel.getZ() * this.dz) < 0.0;
		}
		final double railFactor = MathUtil.invert(MathUtil.normalize(this.dx, this.dz, entity.vel.getX(), entity.vel.getZ()), invert);
		entity.vel.set(railFactor * this.dx, 0.0, railFactor * this.dz);

		// Adjust position of Entity on rail
		IntVector3 railPos = member.getBlockPos();
		entity.loc.set(getFixedPosition(entity, entity.loc.getX(), entity.loc.getY(), entity.loc.getZ(), railPos));
		entity.loc.y.add((double) entity.getHeight() - 0.5);
	}

	/**
	 * Gets the horizontal rail logic to go into the direction specified
	 * 
	 * @param direction to go to
	 * @return Horizontal rail logic for that direction
	 */
	public static RailLogicHorizontal get(BlockFace direction) {
		return values[FaceUtil.faceToNotch(direction)];
	}
}
