package com.forgeessentials.permissions.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.permissions.IContext;
import net.minecraftforge.permissions.PermissionsManager;
import net.minecraftforge.permissions.PermissionsManager.RegisteredPermValue;

import com.forgeessentials.api.permissions.AreaZone;
import com.forgeessentials.api.permissions.Group;
import com.forgeessentials.api.permissions.IPermissionsHelper;
import com.forgeessentials.api.permissions.RootZone;
import com.forgeessentials.api.permissions.ServerZone;
import com.forgeessentials.api.permissions.WorldZone;
import com.forgeessentials.api.permissions.Zone;
import com.forgeessentials.data.api.ClassContainer;
import com.forgeessentials.data.api.DataStorageManager;
import com.forgeessentials.util.UserIdent;
import com.forgeessentials.util.selections.Point;
import com.forgeessentials.util.selections.WorldArea;
import com.forgeessentials.util.selections.WorldPoint;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;

/**
 * 
 * @author Olee
 */
public class ZonedPermissionHelper implements IPermissionsHelper {

	private RootZone rootZone;

	private Group defaultGroup = new Group(GROUP_DEFAULT, null, null, null, 0, 0);

	private Group guestGroup = new Group(GROUP_GUESTS, "[GUEST] ", null, null, 0, 0);

	private Group operatorGroup = new Group(GROUP_OPERATORS, "[OPERATOR] ", null, null, 0, 1);

	private Map<Integer, Zone> zones = new HashMap<Integer, Zone>();

	private Map<String, Group> groups = new HashMap<String, Group>();

	private IZonePersistenceProvider persistenceProvider;

	// ------------------------------------------------------------

	public ZonedPermissionHelper()
	{
		DataStorageManager.registerSaveableType(new ClassContainer(true, Zone.PermissionList.class, String.class, String.class));
		DataStorageManager.registerSaveableType(new ClassContainer(Zone.class));
		DataStorageManager.registerSaveableType(new ClassContainer(RootZone.class));
		DataStorageManager.registerSaveableType(new ClassContainer(ServerZone.class));
		DataStorageManager.registerSaveableType(new ClassContainer(WorldZone.class));
		DataStorageManager.registerSaveableType(new ClassContainer(AreaZone.class));

		FMLCommonHandler.instance().bus().register(this);

		rootZone = new RootZone();
		clear();
	}

	// ------------------------------------------------------------
	// -- Persistence
	// ------------------------------------------------------------

	public void clear()
	{
		zones.clear();
		addZone(rootZone);
		addZone(new ServerZone(rootZone));

		groups.clear();
		groups.put(operatorGroup.getName(), operatorGroup);
		groups.put(defaultGroup.getName(), defaultGroup);
		groups.put(guestGroup.getName(), guestGroup);

		// for (World world : DimensionManager.getWorlds())
		// {
		// getWorldZone(world);
		// }

		// getServerZone().setGroupPermission(DEFAULT_GROUP, "fe.commands.gamemode", false);
		// getServerZone().setGroupPermission(DEFAULT_GROUP, "fe.commands.time", true);
		//
		// WorldZone world0 = getWorldZone(0);
		// world0.setGroupPermission(DEFAULT_GROUP, "fe.commands.gamemode", true);
		// world0.setGroupPermission(DEFAULT_GROUP, "fe.commands.time", false);
	}

	public void setPersistenceProvider(IZonePersistenceProvider persistenceProvider)
	{
		this.persistenceProvider = persistenceProvider;
	}

	public void save()
	{
		if (persistenceProvider != null)
			persistenceProvider.save(rootZone.getServerZone());
	}

	public boolean load()
	{
		if (persistenceProvider != null)
		{
			ServerZone serverZone = persistenceProvider.load();
			if (serverZone != null)
			{
				// Set new server zone
				rootZone.setServerZone(serverZone);

				// Rebuild zones map
				zones.clear();
				addZone(rootZone);
				addZone(serverZone);
				for (WorldZone worldZone : serverZone.getWorldZones().values())
				{
					addZone(worldZone);
					for (AreaZone areaZone : worldZone.getAreaZones())
					{
						addZone(areaZone);
					}
				}
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------
	// -- Utilities
	// ------------------------------------------------------------

	public Set<String> enumRegisteredPermissions()
	{
		Set<String> perms = new TreeSet<String>();
		for (Map<String, String> groupPerms : rootZone.getGroupPermissions().values())
		{
			for (String perm : groupPerms.keySet())
			{
				perms.add(perm);
			}
		}
		return perms;
	}

	public Set<String> enumAllPermissions()
	{
		Set<String> perms = new TreeSet<String>();
		for (Zone zone : zones.values())
		{
			for (Map<String, String> groupPerms : zone.getGroupPermissions().values())
			{
				for (String perm : groupPerms.keySet())
				{
					perms.add(perm);
				}
			}
			for (Map<String, String> playerPerms : zone.getPlayerPermissions().values())
			{
				for (String perm : playerPerms.keySet())
				{
					perms.add(perm);
				}
			}
		}
		return perms;
	}

	public Map<Zone, Map<String, String>> enumUserPermissions(UserIdent ident)
	{
		Map<Zone, Map<String, String>> result = new HashMap<Zone, Map<String, String>>();
		for (Zone zone : zones.values())
		{
			if (zone.getPlayerPermissions(ident) != null)
			{
				Map<String, String> zonePerms = new TreeMap<String, String>();
				zonePerms.putAll(zone.getPlayerPermissions(ident));
				result.put(zone, zonePerms);
			}
		}
		return result;
	}

	public Map<Zone, Map<String, String>> enumGroupPermissions(String group)
	{
		Map<Zone, Map<String, String>> result = new HashMap<Zone, Map<String, String>>();
		for (Zone zone : zones.values())
		{
			if (zone.getGroupPermissions(group) != null)
			{
				Map<String, String> zonePerms = new TreeMap<String, String>();
				zonePerms.putAll(zone.getGroupPermissions(group));
				result.put(zone, zonePerms);
			}
		}
		return result;
	}

	// ------------------------------------------------------------
	// -- Events
	// ------------------------------------------------------------

	@SubscribeEvent
	public void playerLogin(PlayerLoggedInEvent e)
	{
		for (Zone zone : zones.values())
		{
			zone.updatePlayerIdents();
		}
	}

	// ------------------------------------------------------------
	// -- Core permission handling
	// ------------------------------------------------------------

	/**
	 * Main function for permission retrieval. This method should not be used directly. Use the helper methods instead.
	 * 
	 * @param playerId
	 * @param point
	 * @param groups
	 * @param permissionNode
	 * @param isProperty
	 * @return
	 */
	public String getPermission(UserIdent ident, WorldPoint point, WorldArea area, Collection<String> groups, String permissionNode, boolean isProperty)
	{
		// Get world zone
		WorldZone worldZone = getWorldZone(point.getDimension());

		// Get zones in correct order
		List<Zone> zones = new ArrayList<Zone>();
		if (worldZone != null)
		{
			for (Zone zone : worldZone.getAreaZones())
			{
				if (point != null && zone.isInZone(point) || area != null && zone.isInZone(area))
				{
					zones.add(zone);
				}
			}
			zones.add(worldZone);
		}
		zones.add(rootZone.getServerZone());
		zones.add(rootZone);

		return getPermission(zones, ident, groups, permissionNode, isProperty);
	}

	public String getPermission(List<Zone> zones, UserIdent ident, Collection<String> groups, String permissionNode, boolean isProperty)
	{
		// Add default group
		if (groups == null)
		{
			groups = new ArrayList<String>();
		}
		groups.add(defaultGroup.getName());

		// Build node list
		List<String> nodes = new ArrayList<String>();
		nodes.add(permissionNode);
		if (!isProperty)
		{
			String[] nodeParts = permissionNode.split("\\.");
			for (int i = nodeParts.length; i > 0; i--)
			{
				String node = "";
				for (int j = 0; j < i; j++)
				{
					node += nodeParts[j] + ".";
				}
				nodes.add(node + PERMISSION_ASTERIX);
			}
			nodes.add(PERMISSION_ASTERIX);
		}

		// Check player permissions
		if (ident != null)
		{
			for (String node : nodes)
			{
				for (Zone zone : zones)
				{
					String result = zone.getPlayerPermission(ident, node);
					if (result != null)
					{
						return result;
					}
				}
			}
		}

		// Check group permissions
		for (String group : groups)
		{
			for (String node : nodes)
			{
				// Check group permissions
				for (Zone zone : zones)
				{
					String result = zone.getGroupPermission(group, node);
					if (result != null)
					{
						return result;
					}
				}
			}
		}

		return null;
	}

	// ------------------------------------------------------------

	@Override
	public void registerPermissionProperty(String permissionNode, String defaultValue)
	{
		rootZone.setGroupPermissionProperty(defaultGroup.getName(), permissionNode, defaultValue);
	}

	@Override
	public void registerPermission(String permissionNode, PermissionsManager.RegisteredPermValue permLevel)
	{
		if (permLevel == RegisteredPermValue.FALSE)
			rootZone.setGroupPermission(defaultGroup.getName(), permissionNode, false);
		else if (permLevel == RegisteredPermValue.TRUE)
			rootZone.setGroupPermission(defaultGroup.getName(), permissionNode, true);
		else if (permLevel == RegisteredPermValue.OP)
		{
			rootZone.setGroupPermission(defaultGroup.getName(), permissionNode, false);
			rootZone.setGroupPermission(GROUP_OPERATORS, permissionNode, true);
		}
	}

	@Override
	public void setPlayerPermission(UserIdent ident, String permissionNode, boolean value)
	{
		getServerZone().setPlayerPermission(ident, permissionNode, value);
	}

	@Override
	public void setPlayerPermissionProperty(UserIdent ident, String permissionNode, String value)
	{
		getServerZone().setPlayerPermissionProperty(ident, permissionNode, value);
	}

	@Override
	public void setGroupPermission(String group, String permissionNode, boolean value)
	{
		getServerZone().setGroupPermission(group, permissionNode, value);
	}

	@Override
	public void setGroupPermissionProperty(String group, String permissionNode, String value)
	{
		getServerZone().setGroupPermissionProperty(group, permissionNode, value);
	}

	// ------------------------------------------------------------
	// -- IPermissionProvider
	// ------------------------------------------------------------
	/**
	 * Will return the player if set or the commandSender, if it is an instance of {@link EntityPlayer}
	 */
	public static EntityPlayer contextGetPlayerOrCommandPlayer(IContext context)
	{
		return (context.getPlayer() != null) ? context.getPlayer() : (context.getCommandSender() instanceof EntityPlayer ? (EntityPlayer) context
				.getCommandSender() : null);
	}

	private static boolean contextIsConsole(IContext context)
	{
		return context.getPlayer() == null && context.getCommandSender() != null && !(context.getCommandSender() instanceof EntityPlayer);
	}

	public static boolean contextIsPlayer(IContext context)
	{
		return (context.getPlayer() instanceof EntityPlayer) || (context.getCommandSender() instanceof EntityPlayer);
	}

	public static boolean contextHasPlayer(IContext context)
	{
		return (context.getPlayer() instanceof EntityPlayer) || (context.getCommandSender() instanceof EntityPlayer);
	}

	@Override
	public boolean checkPermission(IContext context, String permissionNode)
	{
		// if (contextIsConsole(context)) return true;

		UserIdent ident = null;
		EntityPlayer player = contextGetPlayerOrCommandPlayer(context);
		WorldPoint loc = null;
		WorldArea area = null;
		int dim = 0;

		if (player != null)
		{
			ident = new UserIdent(player);
			// TODO: should be changed to context.getDimension()
			dim = player.dimension;
		}

		if (context.getTargetLocationStart() != null)
		{
			if (context.getTargetLocationEnd() != null)
			{
				area = new WorldArea(dim, new Point(context.getTargetLocationStart()), new Point(context.getTargetLocationEnd()));
			}
			else
			{
				loc = new WorldPoint(dim, context.getTargetLocationStart());
			}
		}
		else if (context.getSourceLocationStart() != null)
		{
			if (context.getSourceLocationEnd() != null)
			{
				area = new WorldArea(dim, new Point(context.getSourceLocationStart()), new Point(context.getSourceLocationEnd()));
			}
			else
			{
				loc = new WorldPoint(dim, context.getSourceLocationStart());
			}
		}
		else
		{
			if (player != null)
			{
				loc = new WorldPoint(player);
			}
		}

		return checkPermission(getPermission(ident, loc, area, getPlayerGroupNames(ident), permissionNode, false));
		// return checkPermission(player, node);
	}

	// ------------------------------------------------------------
	// -- Zones
	// ------------------------------------------------------------

	@Override
	public Collection<Zone> getZones()
	{
		return zones.values();
	}

	@Override
	public Zone getZoneById(int id)
	{
		return zones.get(id);
	}

	@Override
	public Zone getZoneById(String id)
	{
		try
		{
			return zones.get(Integer.parseInt(id));
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	public RootZone getRootZone()
	{
		return rootZone;
	}

	@Override
	public ServerZone getServerZone()
	{
		return getRootZone().getServerZone();
	}

	@Override
	public WorldZone getWorldZone(int dimensionId)
	{
		WorldZone zone = rootZone.getServerZone().getWorldZones().get(dimensionId);
		if (zone == null)
		{
			zone = new WorldZone(getServerZone(), dimensionId);
			addZone(zone);
		}
		return zone;
	}

	@Override
	public WorldZone getWorldZone(World world)
	{
		return getWorldZone(world.provider.dimensionId);
	}

	protected Zone addZone(Zone zone)
	{
		zones.put(zone.getId(), zone);
		return zone;
	}

	@Override
	public List<Zone> getZonesAt(WorldPoint worldPoint)
	{
		WorldZone w = getWorldZone(worldPoint.getDimension());
		List<Zone> result = new ArrayList<Zone>();
		for (AreaZone zone : w.getAreaZones())
			if (zone.isInZone(worldPoint))
				result.add(zone);
		result.add(w);
		result.add(w.getParent());
		return result;
	}

	@Override
	public List<AreaZone> getAreaZonesAt(WorldPoint worldPoint)
	{
		WorldZone w = getWorldZone(worldPoint.getDimension());
		List<AreaZone> result = new ArrayList<AreaZone>();
		for (AreaZone zone : w.getAreaZones())
			if (zone.isInZone(worldPoint))
				result.add(zone);
		return result;
	}

	@Override
	public Zone getZoneAt(WorldPoint worldPoint)
	{
		List<Zone> zones = getZonesAt(worldPoint);
		return zones.isEmpty() ? null : zones.get(0);
	}

	@Override
	public AreaZone getAreaZoneAt(WorldPoint worldPoint)
	{
		List<AreaZone> zones = getAreaZonesAt(worldPoint);
		return zones.isEmpty() ? null : zones.get(0);
	}

	// ------------------------------------------------------------
	// -- Group
	// ------------------------------------------------------------

	@Override
	public Group getGroup(String name)
	{
		return groups.get(name);
	}

	@Override
	public Collection<Group> getGroups()
	{
		return groups.values();
	}

	@Override
	public Group createGroup(String name)
	{
		if (groups.containsKey(name))
			return null;
		Group group = new Group(name);
		groups.put(group.getName(), group);
		return group;
	}

	@Override
	public Group getPrimaryGroup(UserIdent player)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Group> getPlayerGroups(UserIdent ident)
	{
		List<Group> groups = new ArrayList<Group>();

		// TODO: getPlayerGroups !!!!

		if (ident.hasPlayer() && MinecraftServer.getServer().getConfigurationManager().func_152596_g(ident.getPlayer().getGameProfile()))
		{
			groups.add(operatorGroup);
		}
		if (groups.isEmpty())
		{
			groups.add(guestGroup);
		}
		return groups;
	}

	public List<String> getPlayerGroupNames(UserIdent player)
	{
		if (player == null)
		{
			return null;
		}
		List<String> names = new ArrayList<String>();
		for (Group group : getPlayerGroups(player))
		{
			names.add(group.getName());
		}
		return names;
	}

	// ------------------------------------------------------------
	// -- Permission checking
	// ------------------------------------------------------------

	protected boolean checkPermission(String permissionValue)
	{
		if (permissionValue == null)
		{
			return true;
		}
		else
		{
			return !permissionValue.equals(PERMISSION_FALSE);
		}
	}

	// ------------------------------------------------------------

	@Override
	public boolean checkPermission(EntityPlayer player, String permissionNode)
	{
		UserIdent ident = new UserIdent(player);
		return checkPermission(getPermission(ident, new WorldPoint(player), null, getPlayerGroupNames(ident), permissionNode, false));
	}

	@Override
	public String getPermissionProperty(EntityPlayer player, String permissionNode)
	{
		UserIdent ident = new UserIdent(player);
		return getPermission(ident, new WorldPoint(player), null, getPlayerGroupNames(ident), permissionNode, true);
	}

	// ------------------------------------------------------------

	@Override
	public boolean checkPermission(UserIdent ident, String permissionNode)
	{
		return checkPermission(getPermission(ident, ident.hasPlayer() ? new WorldPoint(ident.getPlayer()) : null, null, getPlayerGroupNames(ident),
				permissionNode, false));
	}

	@Override
	public String getPermissionProperty(UserIdent ident, String permissionNode)
	{
		return getPermission(ident, ident.hasPlayer() ? new WorldPoint(ident.getPlayer()) : null, null, getPlayerGroupNames(ident), permissionNode, true);
	}

	@Override
	public Integer getPermissionPropertyInt(UserIdent ident, String permissionNode)
	{
		String value = getPermissionProperty(ident, permissionNode);
		try
		{
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	// ------------------------------------------------------------

	@Override
	public boolean checkPermission(UserIdent ident, WorldPoint targetPoint, String permissionNode)
	{
		return checkPermission(getPermission(ident, targetPoint, null, getPlayerGroupNames(ident), permissionNode, false));
	}

	@Override
	public String getPermissionProperty(UserIdent ident, WorldPoint targetPoint, String permissionNode)
	{
		return getPermission(ident, targetPoint, null, getPlayerGroupNames(ident), permissionNode, true);
	}

	// ------------------------------------------------------------

	@Override
	public boolean checkPermission(UserIdent ident, WorldArea targetArea, String permissionNode)
	{
		return checkPermission(getPermission(ident, null, targetArea, getPlayerGroupNames(ident), permissionNode, false));
	}

	@Override
	public String getPermissionProperty(UserIdent ident, WorldArea targetArea, String permissionNode)
	{
		return getPermission(ident, null, targetArea, getPlayerGroupNames(ident), permissionNode, true);
	}

	// ------------------------------------------------------------

	@Override
	public boolean checkPermission(UserIdent ident, Zone zone, String permissionNode)
	{
		List<Zone> zones = new ArrayList<Zone>();
		zones.add(zone);
		zones.add(rootZone.getServerZone());
		zones.add(rootZone);
		return checkPermission(getPermission(zones, ident, getPlayerGroupNames(ident), permissionNode, false));
	}

	@Override
	public String getPermissionProperty(UserIdent ident, Zone zone, String permissionNode)
	{
		List<Zone> zones = new ArrayList<Zone>();
		zones.add(zone);
		zones.add(rootZone.getServerZone());
		zones.add(rootZone);
		return getPermission(zones, ident, getPlayerGroupNames(ident), permissionNode, true);
	}

	// ------------------------------------------------------------
}
