package com.forgeessentials.chat;

import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;

import com.forgeessentials.data.api.ClassContainer;
import com.forgeessentials.data.api.DataStorageManager;
import com.forgeessentials.data.v2.DataManager;
import com.forgeessentials.util.OutputHandler;
import com.forgeessentials.util.UserIdent;
import com.google.common.collect.HashMultimap;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

public class MailSystem {
    private static HashMultimap<UUID, Mail> map = HashMultimap.create();

    public static void AddMail(Mail mail)
    {
        map.put(mail.getReceiver(), mail);
        DataManager.getInstance().save(Mail.class, mail.getKey());
        DataStorageManager.getReccomendedDriver().saveObject(new ClassContainer(Mail.class), mail);

        EntityPlayer player = UserIdent.getPlayerByUuid(mail.getReceiver());

        if (player != null)
        {
            receiveMail(player);
        }
    }

    public static void LoadAll()
    {
        Map<String, Mail> mails = DataManager.getInstance().loadAll(Mail.class);
        if (!mails.isEmpty())
            for (Mail mail : mails.values())
                map.put(mail.getReceiver(), mail);
        else
        {
            for (Object obj : DataStorageManager.getReccomendedDriver().loadAllObjects(new ClassContainer(Mail.class)))
            {
                Mail mail = (Mail) obj;
                map.put(mail.getReceiver(), mail);
            }
        }
    }

    public static void SaveAll()
    {
        for (Mail mail : map.values())
        {
            DataManager.getInstance().save(Mail.class, mail.getKey());
            DataStorageManager.getReccomendedDriver().saveObject(new ClassContainer(Mail.class), mail);
        }
    }

    public static void receiveMail(EntityPlayer receiver)
    {
        if (map.containsKey(receiver.getPersistentID()))
        {
            OutputHandler.sendMessage(receiver, EnumChatFormatting.GREEN + "--- Your mail ---");
            for (Mail mail : map.get(receiver.getPersistentID()))
            {
                OutputHandler.sendMessage(receiver, EnumChatFormatting.GREEN + "{" + mail.getSender() + "} " + EnumChatFormatting.WHITE + mail.getMessage());
                DataManager.getInstance().delete(Mail.class, mail.getKey());
                DataStorageManager.getReccomendedDriver().deleteObject(new ClassContainer(Mail.class), mail.getKey());
            }
            OutputHandler.sendMessage(receiver, EnumChatFormatting.GREEN + "--- End of mail ---");
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent e)
    {
        receiveMail(e.player);
    }
}
