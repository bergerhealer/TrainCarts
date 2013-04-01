package com.bergerkiller.bukkit.tc.railphysics;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.entity.CommonEntity;
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
	public BlockFace getMovementDirection(MinecartMember<?> member, Vector movement) {
		final BlockFace raildirection = this.getDirection();
		if (this.isSloped() && Math.abs(movement.getX()) < 0.001 && Math.abs(movement.getZ()) < 0.001 && Math.abs(movement.getY()) > 0.001) {
			// Going from vertical down to a slope
			if (movement.getY() > 0.0) {
				return raildirection;
			} else {
				return raildirection.getOppositeFace();
			}
		} else {
			BlockFace direction = FaceUtil.getRailsCartDirection(raildirection);
			if (movement.getX() == 0 || movement.getZ() == 0) {
				// Moving along one axis - simplified calculation
				if (FaceUtil.getFaceYawDifference(direction, FaceUtil.getDirection(movement)) > 90) {
					direction = direction.getOppositeFace();
				}
			} else {
				final float moveYaw;
				// Is the rail connected with the previous rails?
				if (LogicUtil.contains(member.getDirectionFrom(), FaceUtil.getFaces(raildirection))) {
					//       ^
					// > ════╝════
					moveYaw = MathUtil.getLookAtYaw(movement);
				} else {
					// > ════╚════ >
					moveYaw = MathUtil.getLookAtYaw(member.getEntity().getVelocity());
				}
				// Compare with the movement direction to find out whether the opposite is needed
				float diff1 = MathUtil.getAngleDifference(moveYaw, FaceUtil.faceToYaw(direction));
				float diff2 = MathUtil.getAngleDifference(moveYaw, FaceUtil.faceToYaw(direction.getOppositeFace()));
				// Compare with the previous direction to sort out equality problems
				if (diff1 == diff2) {
					diff1 = FaceUtil.getFaceYawDifference(member.getDirectionFrom(), direction);
					diff2 = FaceUtil.getFaceYawDifference(member.getDirectionFrom(), direction.getOppositeFace());
				}
				// Use the opposite direction if needed
				if (diff1 > diff2) {
					direction = direction.getOppositeFace();
				}
			}
			return direction;
		}
	}

	@Override
	public void onPreMove(MinecartMember<?> member) {
		final CommonEntity<?> entity = member.getEntity();
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

		double newLocX = member.getBlockPos().midX() + this.startX;
		double newLocY = (double) member.getBlockPos().y + (double) entity.getHeight();
		double newLocZ = member.getBlockPos().midZ() + this.startZ;
		if (this.alongZ) {
			// Moving along the X-axis
			newLocZ += this.dz * (entity.loc.getZ() - member.getBlockPos().z);
		} else if (this.alongX) {
			// Moving along the Z-axis
			newLocX += this.dx * (entity.loc.getX() - member.getBlockPos().x);
		} else {
			// Curve
			double factor = 2.0 * (this.dx * (entity.loc.getX() - newLocX) + this.dz * (entity.loc.getZ() - newLocZ));
			newLocX += factor * this.dx;
			newLocZ += factor * this.dz;
		}
		entity.loc.set(newLocX, newLocY, newLocZ);
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
