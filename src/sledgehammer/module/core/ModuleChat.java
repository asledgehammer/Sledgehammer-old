package sledgehammer.modules.core;

/*
This file is part of Sledgehammer.

   Sledgehammer is free software: you can redistribute it and/or modify
   it under the terms of the GNU Lesser General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   Sledgehammer is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU Lesser General Public License for more details.

   You should have received a copy of the GNU Lesser General Public License
   along with Sledgehammer. If not, see <http://www.gnu.org/licenses/>.
*/

import java.util.LinkedList;
import java.util.List;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;

import se.krka.kahlua.vm.KahluaTable;
import sledgehammer.SledgeHammer;
import sledgehammer.database.MongoDatabase;
import sledgehammer.database.SledgehammerDatabase;
import sledgehammer.event.ChatMessageEvent;
import sledgehammer.event.ClientEvent;
import sledgehammer.event.RequestChannelsEvent;
import sledgehammer.manager.ChatManager;
import sledgehammer.module.Module;
import sledgehammer.objects.Player;
import sledgehammer.objects.chat.ChannelProperties;
import sledgehammer.objects.chat.ChatChannel;
import sledgehammer.objects.chat.ChatMessage;
import sledgehammer.objects.chat.ChatMessagePlayer;
import sledgehammer.requests.RequestChatChannels;
import zombie.Lua.LuaManager;

public class ModuleChat extends Module {

	public static final String ID      = "ModuleChat";
	public static final String NAME    = "Chat";
	public static final String MODULE  = "core.chat";
	public static final String VERSION = "1.00";
	
	private DBCollection collectionChannels;
	private DBCollection collectionMessages;
	
	public ModuleChat() {}
	
	public void onLoad() {
		SledgehammerDatabase database = SledgeHammer.instance.getDatabase();
		collectionChannels = database.getCollection("sledgehammer_chat_channels");
		collectionMessages = database.getCollection("sledgehammer_chat_messages");
	}

	public void onStart() {
		
		getPermissionsManager().addDefaultPlayerPermission(ChannelProperties.DEFAULT_CONTEXT);
		
		ChatChannel global = new ChatChannel("Global");
		ChatChannel Espanol = new ChatChannel("Espanol");
//		ChatChannel Russia = new ChatChannel("\u0440\u0443\u0441\u0441\u043A\u0438\u0439");
		ChatChannel local  = new ChatChannel("Local");
		ChatChannel pms    = new ChatChannel("PMs");
		
		local.getProperties().streamGlobal(true);
		pms.getProperties().streamGlobal(true);
		
		local.getProperties().setHistory(false);
		global.getProperties().setPublic(true);
		pms.getProperties().setSpeak(false);
		
		local.getProperties().setPublic(false);
		pms.getProperties().setPublic(false);
		
		Espanol.getProperties().setPublic(true);
		Espanol.getProperties().streamGlobal(false);
//		Espanol.getProperties().setContext("sledgehammer.chat.espanol");
//		Russia.getProperties().setPublic(true);
		
		addChannel(global);
		addChannel(local );
		addChannel(Espanol);
		addChannel(pms   );
	}
	
	public void addChannel(ChatChannel channel) {
		getManager().addChatChannel(channel);
	}
	
	public void onClientCommand(ClientEvent event) {
		String command = event.getCommand();
		Player player = event.getPlayer();
		if(command.equalsIgnoreCase("getChatChannels")) {
			// Send request for channels before dispatching.
			RequestChannelsEvent requestEvent = new RequestChannelsEvent(player);
			SledgeHammer.instance.handle(requestEvent);
			RequestChatChannels request = new RequestChatChannels();
			for(ChatChannel channel : getChatManager().getChannels()) {
				request.addChannel(channel);
			}
			for(ChatChannel channel : getChatManager().getChannels()) {
				if(channel.canSee(player)) {					
					request.addChannel(channel);
				}
			}
			event.respond(request);
		} else if(command.equalsIgnoreCase("sendChatMessagePlayer")) {
			// Get the arguments.
			KahluaTable table = event.getTable();
			KahluaTable tableMessage = (KahluaTable) table.rawget("message");
			ChatMessagePlayer message = new ChatMessagePlayer(tableMessage, System.nanoTime());
			message.setPlayerUniqueId(event.getPlayer().getUniqueId());
			message.setPlayerUsername(event.getPlayer().getUsername());
			message.setTime(LuaManager.getHourMinuteJava());
			saveMessage(message);
			String channelName = (String) tableMessage.rawget("channel");
			println("ChannelName: " + channelName);
			ChatChannel channel = SledgeHammer.instance.getChatManager().getChannel(channelName);
			if(channel == null) {
				errorln("Channel does not exist: \"" + channelName + "\".");
				return;
			}
			message.setChannel(channelName);
			channel.addMessage(message);
			ChatMessageEvent e = new ChatMessageEvent(message);
			SledgeHammer.instance.handle(e);
		}
	}
	
	public void getChannelHistory(ChatChannel channel, int length) {
		List<ChatMessage> listMessages = new LinkedList<>();
		int count = 0;
		DBCursor cursor = collectionMessages.find(new BasicDBObject());
		while(count < length && cursor.hasNext()) {
			BasicDBObject object = (BasicDBObject) cursor.next();
			long messageID = Long.parseLong(object.get("id").toString());
			ChatMessage message = getManager().getMessageFromCache(messageID);
			if(message == null) {
				int type = Integer.parseInt(object.get("type").toString());
				if(type == 1) {
					message = new ChatMessage(object);
				} else {
					message = new ChatMessagePlayer(object);
				}
				getManager().addMessageToCache(message);
			}
			listMessages.add(message);
		}
		cursor.close();
		int size = listMessages.size();
		for(int index = 0; index < size; index++) {				
			channel.addMessage(listMessages.get(index));
		}
	}
	
	public String getChannelHistoryName(ChatChannel channel) {
		return getChannelHistoryName(channel.getChannelName());
	}
	
	public String getChannelHistoryName(String channelName) {
		return "sledgehammer_channel_history_" + channelName;
	}
	
	public DBCollection getChannelHistoryCollection(ChatChannel channel) {
		SledgehammerDatabase database = SledgeHammer.instance.getDatabase();
		String collectionName = getChannelHistoryName(channel);
		DBCollection collectionHistory = database.getCollection(collectionName);
		return collectionHistory;
	}
	
	public void saveMessageHistory(ChatChannel channel, ChatMessage message) {
		// Save the message to the channel's history collection.
		DBCollection collectionHistory = getChannelHistoryCollection(channel);
		BasicDBObject object = new BasicDBObject();
		object.put("messageID", message.getMessageID());
		object.put("timeAdded", message.getTime());
		MongoDatabase.upsert(collectionHistory, "messageID", object);
	}
	
	public void saveMessage(ChatMessage message) {
		// Grab the channel for the Message.
		ChatChannel channel = getManager().getChannel(message.getChannel());
		// If the channel is null, the message cannot be saved.
		if(channel == null) {
			println("Message has no channel: " + message.getChannel());
			return;
		}
		// Save the message.
		message.save(collectionMessages);
		saveMessageHistory(channel, message);
	}
	
	public ChannelProperties loadChannelProperties(String name) {
		ChannelProperties properties = new ChannelProperties();
		DBCursor cursor = this.collectionChannels.find(new BasicDBObject("name", name));
		if(cursor.hasNext()) {
			properties.load(cursor.next());
		}
		cursor.close();
		return properties;
	}
	
	public void deleteChannel(ChatChannel channel) {
		if(channel == null) {
			errorln("Channel is null!");
			return;
		}
		println("Removing channel: " + channel.getChannelName());
		getManager().removeChatChannel(channel);
		MongoDatabase.delete(collectionChannels, "name", channel.getChannelName());
		MongoDatabase.delete(collectionMessages, "channel", channel.getChannelName());
		DBCollection collection = SledgeHammer.instance.getDatabase().getCollection(this.getChannelHistoryName(channel));
		collection.drop();
		channel.removeAllPlayers();
	}
	
	public void renameChannelDatabase(ChatChannel chatChannel, String nameOld, String nameNew) {
		chatChannel.getProperties().rename(collectionChannels, nameNew);
		DBCollection collectionHistory = this.getChannelHistoryCollection(chatChannel);
		try {			
			collectionHistory.rename(getChannelHistoryName(nameNew));
		} catch(Exception e) {
			// Collection doesn't exist yet.
		}
		chatChannel.setChannelName(nameNew);
	}
	
	public ChatManager getManager() {
		return SledgeHammer.instance.getChatManager();
	}
	
	public void onUpdate(long delta) {}
	public void onStop() {}
	public void onUnload() {}

	public String getID()         { return ID;      }
	public String getName()       { return NAME;    }
	public String getModuleName() { return MODULE;  }
	public String getVersion()    { return VERSION; }

	public DBCollection getMessageCollection() {
		return this.collectionMessages;
	}

	public DBCollection getChannelCollection() {
		return this.collectionMessages;
	}
}