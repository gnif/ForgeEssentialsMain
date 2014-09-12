package com.forgeessentials.util;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.compress.archivers.dump.InvalidFormatException;

import com.forgeessentials.commands.util.Kit;
import com.forgeessentials.data.api.IReconstructData;
import com.forgeessentials.data.api.SaveableObject;
import com.forgeessentials.data.api.SaveableObject.Reconstructor;
import com.forgeessentials.data.api.SaveableObject.SaveableField;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.PlayerNotFoundException;
import net.minecraft.command.PlayerSelector;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

@SaveableObject(SaveInline = true)
public class UserIdent {

	@SaveableField
	private UUID uuid;

	@SaveableField
	private String username;

	private EntityPlayer player;

	public UserIdent(UUID uuid)
	{
		if (uuid == null)
		{
			throw new IllegalArgumentException();
		}
		this.uuid = uuid;
	}

	public UserIdent(String ident)
	{
		if (ident == null)
		{
			throw new IllegalArgumentException();
		}
		try
		{
			this.uuid = UUID.fromString(ident);
		}
		catch (IllegalArgumentException e)
		{
			this.username = ident;
		}
	}

	public UserIdent(EntityPlayer player)
	{
		if (player == null)
			throw new IllegalArgumentException();
		this.player = player;
		this.uuid = player.getPersistentID();
		this.username = player.getCommandSenderName();
	}

	public UserIdent(UUID uuid, String username)
	{
		if (uuid == null && (username == null || username.isEmpty()))
			throw new IllegalArgumentException();
		this.uuid = uuid;
		this.username = username;
	}

	public UserIdent(String uuid, String username)
	{
		if (uuid == null && (username == null || username.isEmpty()))
			throw new IllegalArgumentException();
		this.username = username;
		if (uuid != null && !uuid.isEmpty())
		{
			this.uuid = UUID.fromString(uuid);
			this.username = username;
		}
	}

	// ------------------------------------------------------------

	public void identifyUser()
	{
		if (uuid == null)
		{
			uuid = getUuidByUsername(username);
		}
		else if (username == null)
		{
			username = getUsernameByUuid(uuid);
		}
		else if (player == null && uuid != null)
		{
			player = getPlayerByUuid(uuid);
		}
	}

	public void updateUsername()
	{
		username = getUsernameByUuid(uuid);
	}

	public boolean wasValidUUID()
	{
		return uuid != null;
	}

	public boolean hasUsername()
	{
		identifyUser();
		return username != null;
	}

	public boolean hasUUID()
	{
		identifyUser();
		return uuid != null;
	}

	public boolean hasPlayer()
	{
		identifyUser();
		return player != null;
	}

	// ------------------------------------------------------------

	public UUID getUuid()
	{
		identifyUser();
		return uuid;
	}

	public String getUsername()
	{
		identifyUser();
		return username;
	}

	public EntityPlayer getPlayer()
	{
		identifyUser();
		return player;
	}

	public String getUsernameOrUUID()
	{
		identifyUser();
		return username == null ? uuid.toString() : username;
	}

	// ------------------------------------------------------------

	@Override
	public String toString()
	{
		identifyUser();
		return "(" + (uuid == null ? "" : uuid.toString()) + "|" + username + ")";
	}

	@Override
	public int hashCode()
	{
		identifyUser();
		if (uuid == null)
		{
			// throw new PlayerNotFoundException();
			return username.hashCode();
		}
		else
		{
			return uuid.hashCode();
		}
	}

	@Override
	public boolean equals(Object other)
	{
		if (other instanceof String)
		{
			identifyUser();
			if (this.uuid != null)
			{
				try
				{
					UUID otherUUID = UUID.fromString((String) other);
					return this.uuid.equals(otherUUID);
				}
				catch (IllegalArgumentException e)
				{
					// Do nothing
				}
			}
			return this.username.equals(other);
		}
		else if (other instanceof UserIdent)
		{
			UserIdent ident = (UserIdent) other;
			identifyUser();
			ident.identifyUser();
			if (uuid != null && ident.uuid != null)
				return uuid.equals(ident.uuid);
			if (username != null && ident.username != null)
				return uuid.equals(ident.uuid);
			return false;
		}
		else if (other instanceof UUID)
		{
			identifyUser();
			return other.equals(uuid);
		}
		else
		{
			return false;
		}
	}

	// ------------------------------------------------------------

	@Reconstructor
	private static UserIdent reconstruct(IReconstructData tag)
	{
		return new UserIdent((UUID) tag.getFieldValue("uuid"), (String) tag.getFieldValue("username"));
	}

	public static UserIdent fromString(String string)
	{
		if (string.charAt(0) != '(' || string.charAt(string.length() - 1) != ')' || string.indexOf('|') < 0)
		{
			throw new IllegalArgumentException("UserIdent string needs to be in the format \"(<uuid>|<username>)\"");
		}
		String[] parts = string.substring(1, string.length() - 2).split("\\|", 1);
		return new UserIdent(UUID.fromString(parts[0]), parts[1]);
	}

	public static String getUsernameByUuid(String uuid)
	{
		return getUsernameByUuid(UUID.fromString(uuid));
	}

	public static String getUsernameByUuid(UUID uuid)
	{
		GameProfile profile = MinecraftServer.getServer().func_152358_ax().func_152652_a(uuid);
		if (profile == null)
			return null;
		return profile.getName();
	}

	public static EntityPlayerMP getPlayerByUuid(UUID uuid)
	{
		for (EntityPlayerMP player : (List<EntityPlayerMP>) FMLCommonHandler.instance().getSidedDelegate().getServer().getConfigurationManager().playerEntityList)
		{
			if (player.getGameProfile().getId().equals(uuid))
			{
				return player;
			}
		}
		return null;
	}

	public static EntityPlayerMP getPlayerByUsername(String name)
	{
		// EntityPlayerMP player = PlayerSelector.matchOnePlayer(sender, name);
		for (EntityPlayerMP player : (List<EntityPlayerMP>) FMLCommonHandler.instance().getSidedDelegate().getServer().getConfigurationManager().playerEntityList)
		{
			if (player.getGameProfile().getName().equals(name))
			{
				return player;
			}
		}
		return null;
	}

	public static EntityPlayerMP getPlayerByMatch(ICommandSender sender, String match)
	{
		EntityPlayerMP player = PlayerSelector.matchOnePlayer(sender, match);
		if (player != null)
			return player;
		else
			return getPlayerByUsername(match);
	}

	public static UUID getUuidByUsername(String username)
	{
		EntityPlayerMP player = MinecraftServer.getServer().getConfigurationManager().func_152612_a(username);
		if (player == null)
			return null;
		return player.getGameProfile().getId();
	}


}
