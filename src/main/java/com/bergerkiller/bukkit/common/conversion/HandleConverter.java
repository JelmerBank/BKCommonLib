package com.bergerkiller.bukkit.common.conversion;

import net.minecraft.server.v1_4_R1.*;

import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.v1_4_R1.CraftChunk;
import org.bukkit.craftbukkit.v1_4_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_4_R1.block.*;
import org.bukkit.craftbukkit.v1_4_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_4_R1.inventory.CraftInventory;
import org.bukkit.craftbukkit.v1_4_R1.inventory.CraftItemStack;

import com.bergerkiller.bukkit.common.reflection.classes.BlockStateRef;
import com.bergerkiller.bukkit.common.reflection.classes.CraftItemStackRef;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.wrappers.nbt.CommonTag;

/**
 * Converter for converting to internal handles (from wrapper classes)
 */
public abstract class HandleConverter extends BasicConverter<Object> {
	public static final HandleConverter toEntityHandle = new HandleConverter(Entity.class) {
		@Override
		public Object convert(Object value, Object def) {
			if (value instanceof Entity) {
				return value;
			} else if (value instanceof CraftEntity) {
				return ((CraftEntity) value).getHandle();
			} else {
				return def;
			}
		}

		@Override
		public boolean isCastingSupported() {
			return true;
		}
	};
	public static final HandleConverter toWorldHandle = new HandleConverter(World.class) {
		@Override
		public Object convert(Object value, Object def) {
			if (value instanceof World) {
				return value;
			} else {
				org.bukkit.World world = WrapperConverter.toWorld.convert(value);
				if (world instanceof CraftWorld) {
					return ((CraftWorld) world).getHandle();
				} else {
					return def;
				}
			}
		}

		@Override
		public boolean isCastingSupported() {
			return true;
		}
	};
	public static final HandleConverter toChunkHandle = new HandleConverter(Chunk.class) {
		@Override
		public Object convert(Object value, Object def) {
			if (value instanceof Chunk) {
				return value;
			} else {
				org.bukkit.Chunk chunk = WrapperConverter.toChunk.convert(value);
				if (chunk instanceof CraftChunk) {
					return ((CraftChunk) value).getHandle();
				} else {
					return def;
				}
			}
		}
	};
	public static final HandleConverter toItemStackHandle = new HandleConverter(ItemStack.class) {
		@Override
		public Object convert(Object value, Object def) {
			if (value instanceof ItemStack) {
				return value;
			} else if (value instanceof CraftItemStack) {
				return CraftItemStackRef.handle.get(value);
			} else if (value instanceof org.bukkit.inventory.ItemStack) {
				return CraftItemStack.asNMSCopy((org.bukkit.inventory.ItemStack) value);
			} else {
				return def;
			}
		}
	};
	public static final HandleConverter toTileEntityHandle = new HandleConverter(TileEntity.class) {
		@Override
		public Object convert(Object value, Object def) {
			if (value instanceof TileEntity) {
				return value;
			} else if (value instanceof CraftSign) {
				return BlockStateRef.SIGN.get(value);
			} else if (value instanceof CraftFurnace) {
				return BlockStateRef.FURNACE.get(value);
			} else if (value instanceof CraftChest) {
				return BlockStateRef.CHEST.get(value);
			} else if (value instanceof CraftDispenser) {
				return BlockStateRef.DISPENSER.get(value);
			} else if (value instanceof BlockState) {
				value = ((BlockState) value).getBlock();
			}
			if (value instanceof org.bukkit.block.Block) {
				// Obtain the tile entity at this block if possible
				org.bukkit.block.Block b = (org.bukkit.block.Block) value;
				World world = (World) toWorldHandle.convert(b.getWorld());
				if (world != null) {
					return LogicUtil.fixNull(world.getTileEntity(b.getX(), b.getY(), b.getZ()), def);
				}
			}
			return def;
		}

		@Override
		public boolean isCastingSupported() {
			return true;
		}
	};
	public static final HandleConverter toInventoryHandle = new HandleConverter(IInventory.class) {
		@Override
		public Object convert(Object value, Object def) {
			if (value instanceof IInventory) {
				return value;
			} else if (value instanceof CraftInventory) {
				return LogicUtil.fixNull(((CraftInventory) value).getInventory(), def);
			}
			return def;
		}
	};
	public static final HandleConverter toDataWatcherHandle = new HandleConverter(DataWatcher.class) {
		@Override
		public Object convert(Object value, Object def) {
			if (value instanceof DataWatcher) {
				return value;
			} else if (value instanceof com.bergerkiller.bukkit.common.wrappers.DataWatcher) {
				return ((com.bergerkiller.bukkit.common.wrappers.DataWatcher) value).getHandle();
			} else if (value instanceof Entity) {
				return ((Entity) value).getDataWatcher();
			} else {
				return def;
			}
		}
	};
	public static final HandleConverter toNBTTagHandle = new HandleConverter(NBTBase.class) {
		@Override
		public Object convert(Object value, Object def) {
			if (value instanceof NBTBase) {
				return value;
			} else if (value instanceof CommonTag) {
				return ((CommonTag<?>) value).getHandle();
			} else {
				return def;
			}
		}

		@Override
		public boolean isCastingSupported() {
			return true;
		}
	};
	public static final HandleConverter toItemHandle = new HandleConverter(Item.class) {
		@Override
		public Object convert(Object value, Object def) {
			if (value instanceof Item) {
				return value;
			}
			Integer id = PropertyConverter.toItemId.convert(value);
			if (id != null) {
				int idInt = id.intValue();
				if (LogicUtil.isInBounds(Item.byId, idInt)) {
					return LogicUtil.fixNull(Item.byId[idInt], def);
				}
			}
			return def;
		}

		@Override
		public boolean isCastingSupported() {
			return true;
		}
	};
	public static final HandleConverter toBlockHandle = new HandleConverter(Block.class) {
		@Override
		public Object convert(Object value, Object def) {
			if (value instanceof Block) {
				return value;
			}
			Integer id = PropertyConverter.toItemId.convert(value);
			if (id != null) {
				int idInt = id.intValue();
				if (LogicUtil.isInBounds(Block.byId, idInt)) {
					return LogicUtil.fixNull(Block.byId[idInt], def);
				}
			}
			return def;
		}

		@Override
		public boolean isCastingSupported() {
			return true;
		}
	};

	@SuppressWarnings("unchecked")
	public HandleConverter(Class<?> outputType) {
		super((Class<Object>) outputType);
	}
}
