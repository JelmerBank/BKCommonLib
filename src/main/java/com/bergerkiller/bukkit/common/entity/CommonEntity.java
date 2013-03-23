package com.bergerkiller.bukkit.common.entity;

import java.util.List;
import java.util.ListIterator;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_5_R2.CraftServer;
import org.bukkit.craftbukkit.v1_5_R2.entity.CraftEntity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import net.minecraft.server.v1_5_R2.Chunk;
import net.minecraft.server.v1_5_R2.Entity;
import net.minecraft.server.v1_5_R2.EntityTrackerEntry;
import net.minecraft.server.v1_5_R2.World;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.bases.ExtendedEntity;
import com.bergerkiller.bukkit.common.controller.DefaultEntityController;
import com.bergerkiller.bukkit.common.controller.DefaultEntityNetworkController;
import com.bergerkiller.bukkit.common.controller.EntityController;
import com.bergerkiller.bukkit.common.controller.EntityNetworkController;
import com.bergerkiller.bukkit.common.conversion.Conversion;
import com.bergerkiller.bukkit.common.entity.nms.NMSEntityHook;
import com.bergerkiller.bukkit.common.entity.nms.NMSEntityTrackerEntry;
import com.bergerkiller.bukkit.common.internal.CommonNMS;
import com.bergerkiller.bukkit.common.protocol.PacketFields;
import com.bergerkiller.bukkit.common.reflection.classes.EntityRef;
import com.bergerkiller.bukkit.common.reflection.classes.EntityTrackerEntryRef;
import com.bergerkiller.bukkit.common.reflection.classes.WorldServerRef;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.EntityTracker;
import com.bergerkiller.bukkit.common.wrappers.IntHashMap;

/**
 * Wrapper class for additional methods Bukkit can't or doesn't provide.
 * 
 * @param <T> - type of Entity
 */
public class CommonEntity<T extends org.bukkit.entity.Entity> extends ExtendedEntity<T> {
	public CommonEntity(T entity) {
		super(entity);
	}

	/**
	 * Gets the Entity Network Controller currently assigned to this Entity.
	 * If none is available, this method returns Null.
	 * If no custom network controller is set, this method returns a new
	 * {@link com.bergerkiller.bukkit.common.controller.DefaultEntityNetworkController DefaultEntityNetworkController} instance.
	 * 
	 * @return Entity Network Controller, or null if not available
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public EntityNetworkController<CommonEntity<T>> getNetworkController() {
		final EntityNetworkController result;
		final Object entityTrackerEntry = WorldUtil.getTrackerEntry(entity);
		if (entityTrackerEntry instanceof NMSEntityTrackerEntry) {
			result = ((NMSEntityTrackerEntry) entityTrackerEntry).getController();
		} else if (entityTrackerEntry instanceof EntityTrackerEntry) {
			result = new DefaultEntityNetworkController();
			result.bind(this, entityTrackerEntry);
		} else {
			return null;
		}
		return result;
	}

	/**
	 * Sets an Entity Network Controller for this Entity.
	 * To stop tracking this minecart, pass in Null.
	 * To default back to the net.minecraft.server implementation, pass in a new
	 * {@link com.bergerkiller.bukkit.common.controller.DefaultEntityNetworkController DefaultEntityNetworkController} instance.<br>
	 * <br>
	 * This method only works if the Entity world has previously been set.
	 * 
	 * @param controller to set to
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	public void setNetworkController(EntityNetworkController controller) {
		if (getWorld() == null) {
			throw new RuntimeException("Can not set the network controller when no world is known! (need to spawn it?)");
		}
		final EntityTracker tracker = WorldUtil.getTracker(getWorld());
		final EntityNetworkController<CommonEntity<T>> oldController = getNetworkController();
		if (oldController == controller) {
			return;
		}
		// Detach previous controller
		if (oldController != null) {
			oldController.bind(null, null);
		}
		if (controller == null) {
			// Stop tracking - nothing special
			tracker.stopTracking(entity);
		} else {
			// Obtain previous and new replacement entry
			final Object oldEntry = tracker.getEntry(entity);
			final Object newEntry;
			if (controller instanceof DefaultEntityNetworkController) {
				if (oldEntry == null) {
					final CommonEntityType type = CommonEntityType.byEntity(entity);
					newEntry = new EntityTrackerEntry(getHandle(Entity.class), 
							type.networkViewDistance, type.networkUpdateInterval, type.networkIsMobile);
				} else {
					newEntry = oldEntry;
				}
			} else {
				newEntry = new NMSEntityTrackerEntry(entity, controller, oldEntry);
			}
			// Attach (new?) entry to the world
			if (oldEntry != newEntry) {
				tracker.setEntry(entity, newEntry);
			}
			// Attach the data to the controller
			controller.bind(this, newEntry);
		}
	}

	/**
	 * Gets the Entity Controller currently assigned to this Entity.
	 * If no custom controller is set, this method returns a new 
	 * {@link com.bergerkiller.bukkit.common.controller.DefaultEntityController DefaultEntityController} instance.
	 * 
	 * @return Entity Controller
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public EntityController<CommonEntity<T>> getController() {
		if (isHooked()) {
			return (EntityController<CommonEntity<T>>) getHandle(NMSEntityHook.class).getController();
		}
		final EntityController controller = new DefaultEntityController();
		controller.bind(this);
		return controller;
	}

	/**
	 * Checks whether this particular Entity supports the use of Entity Controllers.
	 * If this method returns True, {@link setController(controller)} can be used.<br><br>
	 * 
	 * Note that Entity Network Controllers are always supported.
	 * 
	 * @return True if Entity Controllers are supported, False if not
	 */
	public boolean hasControllerSupport() {
		if (isHooked()) {
			return true;
		} else if (getHandle() == null) {
			return false;
		} else {
			return getHandle().getClass().getName().startsWith(Common.NMS_ROOT);
		}
	}

	/**
	 * Checks whether the Entity is a BKCommonLib hook
	 * 
	 * @return True if hooked, False if not
	 */
	protected boolean isHooked() {
		return getHandle() instanceof NMSEntityHook;
	}

	/**
	 * Replaces the current entity, if needed, with the BKCommonLib Hook entity type
	 */
	@SuppressWarnings("unchecked")
	protected void prepareHook() {
		final Entity oldInstance = getHandle(Entity.class);
		if (oldInstance instanceof NMSEntityHook) {
			// Already hooked
			return;
		}

		// Check whether conversion is allowed
		final String oldInstanceName = oldInstance.getClass().getName();
		if (!oldInstanceName.startsWith(Common.NMS_ROOT)) {
			throw new RuntimeException("Can not assign controllers to a custom Entity Type (" + oldInstanceName + ")");
		}
		final CommonEntityType type = CommonEntityType.byEntity(entity);
		if (!type.hasNMSEntity()) {
			throw new RuntimeException("Entity of type '" + type.entityType + "' has no Controller support!");
		}
		// Respawn the entity and attach the controller
		try {
			// Create a new entity instance and perform data/property transfer
			final Entity newInstance = (Entity) type.createNMSHookEntity(this);
			type.nmsType.transfer(oldInstance, newInstance);
			oldInstance.dead = true;
			newInstance.dead = false;
			oldInstance.valid = false;
			newInstance.valid = true;

			// *** Bukkit Entity ***
			((CraftEntity) entity).setHandle(newInstance);

			// *** Give the old entity a new Bukkit Entity ***
			EntityRef.bukkitEntity.set(oldInstance, CraftEntity.getEntity((CraftServer) Bukkit.getServer(), oldInstance));

			// *** Passenger/Vehicle ***
			if (newInstance.vehicle != null) {
				newInstance.vehicle.passenger = newInstance;
			}
			if (newInstance.passenger != null) {
				newInstance.passenger.vehicle = newInstance;
			}

			// Only do this replacement logic for Entities that are already spawned
			if (this.isSpawned()) {
				// Now proceed to replace this NMS Entity in all places imaginable.
				// First load the chunk so we can at least work on something
				Chunk chunk = CommonNMS.getNative(getWorld().getChunkAt(getChunkX(), getChunkZ()));

				// *** Entities By ID Map ***
				final IntHashMap<Object> entitiesById = WorldServerRef.entitiesById.get(oldInstance.world);
				if (entitiesById.remove(oldInstance.id) == null) {
					CommonUtil.nextTick(new Runnable() {
						public void run() {
							entitiesById.put(newInstance.id, newInstance);
						}
					});
				}
				entitiesById.put(newInstance.id, newInstance);

				// *** EntityTrackerEntry ***
				final EntityTracker tracker = WorldUtil.getTracker(getWorld());
				Object entry = tracker.getEntry(entity);
				if (entry != null) {
					EntityTrackerEntryRef.tracker.set(entry, entity);
				}
				if (hasPassenger()) {
					entry = tracker.getEntry(getPassenger());
					if (entry != null) {
						EntityTrackerEntryRef.vehicle.set(entry, entity);
					}
				}

				// *** World ***
				ListIterator<Entity> iter = oldInstance.world.entityList.listIterator();
				while (iter.hasNext()) {
					if (iter.next().id == oldInstance.id) {
						iter.set(newInstance);
						break;
					}
				}

				// *** Chunk ***
				final int chunkY = getChunkY();
				if (!replaceInChunk(chunk, chunkY, oldInstance, newInstance)) {
					for (int y = 0; y < chunk.entitySlices.length; y++) {
						if (y != chunkY && replaceInChunk(chunk, y, oldInstance, newInstance)) {
							break;
						}
					}
				}
			}
		} catch (Throwable t) {
			throw new RuntimeException("Failed to set controller:", t);
		}
	}

	@SuppressWarnings({"unchecked"})
	private static boolean replaceInChunk(Chunk chunk, int chunkY, Entity toreplace, Entity with) {
		List<Entity> list = chunk.entitySlices[chunkY];
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).id == toreplace.id) {
				list.set(i, with);
				//set invalid
				chunk.m = true;
				return true;
			}
		}
		return false;
	}

	/**
	 * Sets an Entity Controller for this Entity.
	 * This method throws an Exception if this kind of Entity is not supported.
	 * 
	 * @param controller to set to
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	public void setController(EntityController controller) {
		// Prepare the hook
		this.prepareHook();

		// If null, resolve to the default type
		if (controller == null) {
			controller = new DefaultEntityController();
		}
		getController().bind(null);
		controller.bind(this);
	}

	@Override
	public boolean teleport(Location location, TeleportCause cause) {
		if (isDead()) {
			return false;
		}
		// Preparations prior to teleportation
		final Entity entityHandle = CommonNMS.getNative(entity);
		final Entity oldPassenger = entityHandle.passenger;
		final World newworld = CommonNMS.getNative(location.getWorld());
		final boolean isWorldChange = entityHandle.world != newworld;
		final EntityNetworkController<?> oldNetworkController = getNetworkController();
		WorldUtil.loadChunks(location, 3);

		// If in a vehicle, make sure we eject first
		if (entityHandle.vehicle != null) {
			entityHandle.vehicle.passenger = null;
			entityHandle.vehicle = null;
			doAttach(Conversion.toEntity.convert(entityHandle), null);
		}
		// If vehicle, eject the passenger first
		if (entityHandle.passenger != null) {
			entityHandle.passenger.vehicle = null;
			entityHandle.passenger = null;
			doAttach(Conversion.toEntity.convert(oldPassenger), null);
		}

		// Perform actual teleportation
		final boolean success;
		if (!isWorldChange || entity instanceof Player) {
			success = entity.teleport(location, cause);
		} else {
			entityHandle.world.removeEntity(entityHandle);
			entityHandle.dead = false;
			entityHandle.world = newworld;
			entityHandle.setLocation(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
			entityHandle.world.addEntity(entityHandle);
			success = true;
		}
		if (isWorldChange && !(oldNetworkController instanceof DefaultEntityNetworkController)) {
			this.setNetworkController(oldNetworkController);
		}

		// If there was a passenger, teleport it and let passenger enter again
		if (oldPassenger != null) {
			final CommonEntity<?> passenger = get(Conversion.toEntity.convert(oldPassenger));
			if (isWorldChange) {
				// Do this teleport and enter the next tick
				if (passenger.teleport(location)) {
					CommonUtil.nextTick(new Runnable() {
						public void run() {
							CommonUtil.nextTick(new Runnable() {
								public void run() {
									entityHandle.passenger = oldPassenger;
									entityHandle.passenger.vehicle = entityHandle;
									doAttach(Conversion.toEntity.convert(oldPassenger), Conversion.toEntity.convert(entityHandle));
								}
							});
							//passenger.getHandle(Entity.class).setPassengerOf(entityHandle);
							//oldPassenger.setPassengerOf(entityHandle);
						}
					});
					//entityHandle.passenger = oldPassenger;
					//entityHandle.passenger.vehicle = entityHandle;
					//doAttach(Conversion.toEntity.convert(oldPassenger), Conversion.toEntity.convert(entityHandle));
					//doAttach(Conversion.toEntity.convert(oldPassenger), Conversion.toEntity.convert(entityHandle));
				}
			} else {
				// Set passenger directly
				entityHandle.passenger = oldPassenger;
				entityHandle.passenger.vehicle = entityHandle;
				doAttach(Conversion.toEntity.convert(oldPassenger), Conversion.toEntity.convert(entityHandle));
			}
		}
		
		if (oldPassenger != null && (!isWorldChange || get(Conversion.toEntity.convert(oldPassenger)).teleport(location, cause))) {
			doAttach(Conversion.toEntity.convert(oldPassenger), Conversion.toEntity.convert(entityHandle));
		}

//		if (isWorldChange && !(entityHandle instanceof EntityPlayer)) {
//			if (oldPassenger != null) {
//				entityHandle.passenger = null;
//				oldPassenger.vehicle = null;
//				if (get(Conversion.toEntity.convert(oldPassenger)).teleport(location, cause)) {
//					CommonUtil.nextTick(new Runnable() {
//						public void run() {
//							oldPassenger.setPassengerOf(entityHandle);
//						}
//					});
//				}
//			}
//		} else {
//
//			succ = entity.teleport(location, cause);
//			// If there was a passenger, let passenger enter again
//			if (oldPassenger != null) {
//				doAttach(Conversion.toEntity.convert(oldPassenger), Conversion.toEntity.convert(entityHandle));
//				oldPassenger.vehicle = entityHandle;
//				entityHandle.passenger = oldPassenger;
//			}
//		}
		return success;
	}

	private static void doAttach(org.bukkit.entity.Entity passenger, org.bukkit.entity.Entity vehicle) {
		if (!(passenger instanceof Player)) {
			return;
		}
		PacketUtil.sendPacket((Player) passenger, PacketFields.ATTACH_ENTITY.newInstance(passenger, vehicle));
	}

	/**
	 * Spawns this Entity at the Location and using the network controller specified.
	 * 
	 * @param location to spawn at
	 * @param networkController to assign to the Entity after spawning
	 * @return True if spawning occurred, False if not
	 * @see {@link spawn(Location location)}
	 */
	@SuppressWarnings("rawtypes")
	public final boolean spawn(Location location, EntityNetworkController networkController) {
		final boolean spawned = spawn(location);
		this.setNetworkController(networkController);
		return spawned;
	}

	/**
	 * Spawns this Entity at the Location specified.
	 * Note that if important properties have to be set beforehand, this should be done first.
	 * It is recommended to set Entity Controllers before spawning, not after.
	 * This method will trigger Entity spawning events.
	 * The network controller can ONLY be set after spawning.
	 * To be on the safe side, use the Network Controller spawn alternative.
	 * 
	 * @param location to spawn at
	 * @return True if the Entity spawned, False if not (and just teleported)
	 */
	public boolean spawn(Location location) {
		if (this.isSpawned()) {
			teleport(location);
			return false;
		}
		last.set(loc.set(location));
		EntityUtil.addEntity(entity);
		// Perform controller attaching
		getController().onAttached();
		getNetworkController().onAttached();
		return true;
	}

	/**
	 * Obtains a (new) {@link CommonEntity} instance providing additional methods for the Entity specified.
	 * This method new returns null.
	 * 
	 * @param entity to get a CommonEntity for
	 * @return a (new) CommonEntity instance for the Entity
	 */
	@SuppressWarnings("unchecked")
	public static <T extends org.bukkit.entity.Entity> CommonEntity<T> get(T entity) {
		final Object handle = Conversion.toEntityHandle.convert(entity);
		if (handle instanceof NMSEntityHook) {
			EntityController<?> controller = ((NMSEntityHook) handle).getController();
			if (controller != null) {
				return (CommonEntity<T>) controller.getEntity();
			}
		}
		return CommonEntityType.byNMSEntity(handle).createCommonEntity(entity);
	}

	/**
	 * Creates (but does not spawn) a new Common Entity backed by a proper Entity.
	 * 
	 * @param entityType to create
	 * @return a new CommonEntity type instance
	 */
	public static CommonEntity<?> create(EntityType entityType) {
		CommonEntityType type = CommonEntityType.byEntityType(entityType);
		if (type == CommonEntityType.UNKNOWN) {
			throw new IllegalArgumentException("The Entity Type '" + entityType + "' is invalid!");
		}
		final CommonEntity<org.bukkit.entity.Entity> entity = type.createCommonEntity(null);
		// Spawn a new NMS Entity
		Entity handle;
		if (type.hasNMSEntity()) {
			handle = (Entity) type.createNMSHookEntity(entity);
		} else {
			throw new RuntimeException("The Entity Type '"  + entityType + "' has no suitable Entity constructor to use!");
		}
		entity.entity = Conversion.toEntity.convert(handle);
		// Create a new CommonEntity and done
		return entity;
	}
}
