package sledgehammer.module;

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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sledgehammer.SledgeHammer;
import sledgehammer.event.ClientEvent;
import sledgehammer.event.Event;
import sledgehammer.interfaces.CommandListener;
import sledgehammer.interfaces.EventListener;
import sledgehammer.interfaces.ExceptionListener;
import sledgehammer.interfaces.LogListener;
import sledgehammer.interfaces.ModuleSettingsHandler;
import sledgehammer.interfaces.PermissionsHandler;
import sledgehammer.manager.ChatManager;
import sledgehammer.manager.EventManager;
import sledgehammer.manager.ModuleManager;
import sledgehammer.manager.PermissionsManager;
import sledgehammer.objects.Player;
import sledgehammer.objects.chat.ChatChannel;
import sledgehammer.util.INI;
import sledgehammer.util.Printable;

public abstract class Module extends Printable {

	private INI ini;

	private File iniFile;

	public boolean loadedSettings = false;

	private boolean loaded = false;

	private boolean started = false;

	private String jarName = null;

	private Map<String, String> pluginSettings = new HashMap<>();

	public void loadSettings(ModuleSettingsHandler handler) {

		if (handler == null)
			throw new IllegalArgumentException("Settings Handler given is null!");

		loadedSettings = false;

		if (ini == null)
			getINI();

		if (iniFile.exists()) {
			handler.createSettings(getINI());
			try {
				ini.read();
				loadedSettings = true;
			} catch (IOException e) {
				println("Failed to read settings.");
				e.printStackTrace();
			}
		} else {
			println("WARNING: No settings file found. Creating one.");
			println("WARNING: " + getName() + " may require modified settings to run properly.");
			println("Settings file is located at: " + ini.getFile().getAbsolutePath());
			handler.createSettings(ini);
			loadedSettings = true;
			try {
				ini.save();
			} catch (IOException e) {
				println("Failed to save settings.");
				e.printStackTrace();
			}
		}
	}

	public void register(CommandListener listener) {
			SledgeHammer.instance.register(listener);
	}

	public boolean stopModule() {
		if (loaded) {
			try {
				if (started) {
					this.onStop();
				} else {
					println("Module is already stopped.");
				}
			} catch (Exception e) {
				println("Failed to safely stop module.");
				e.printStackTrace();
			}
		}
		loaded = false;
		return true;
	}

	public boolean loadModule() {
		try {
			onLoad();
			loaded = true;
			return true;
		} catch (Exception e) {
			println("Failed to load module.");
			loaded = false;
			e.printStackTrace();

		}
		return false;
	}

	public boolean unloadModule() {
		try {
			if (loaded) {
				this.onUnload();
			} else {
			}
		} catch (Exception e) {
			println("Failed to safely unload module.");
			e.printStackTrace();
		}
		return true;
	}

	public void register(EventListener listener) {
		String[] types = listener.getTypes();
		if (types == null) {
			throw new IllegalArgumentException("EventListener getTypes() array is null!");
		}
		for (String type : types) {
			SledgeHammer.instance.register(type, listener);
		}
	}
	
	public void unregister(EventListener listener) {
		getEventManager().unregister(listener);
	}
	
	public void unregister(CommandListener listener) {
		getEventManager().unregister(listener);
	}
	
	public void unregister(LogListener listener) {
		getEventManager().unregister(listener);
	}
	
	public void unregister(ExceptionListener listener) {
		getEventManager().unregister(listener);
	}
	
	public void register(PermissionsHandler handler) {
		getPermissionsManager().registerPermissionsHandler(handler);
	}
	
	public void unregister(PermissionsHandler handler) {
		getPermissionsManager().unregister(handler);
	}

	public void startModule() {
		if (!started) {
			started = true;
			onStart();
		} else {
			println("Module is already started.");
		}
	}

	public INI getINI() {
		if (ini == null) {
			iniFile = new File("plugins" + File.separator
					+ getJarName() + ".ini");
			ini = new INI(iniFile);
		}

		return this.ini;
	}

	public void unload() {
		started = false;
		getModuleManager().unloadModule(this, true);
	}

	public EventManager getEventManager() {
		return SledgeHammer.instance.getEventManager();
	}
	
	public ModuleManager getModuleManager() {
		return SledgeHammer.instance.getModuleManager();
	}

	public String getPermissionDeniedMessage() {
		return SledgeHammer.instance.getPermissionsManager().getPermissionDeniedMessage();
	}

	public Map<String, String> getPluginSettings() {
		return this.pluginSettings;
	}

	public void setPluginSettings(Map<String, String> map) {
		this.pluginSettings = map;
	}

	public String getJarName() {
		return this.jarName;
	}

	public void setJarName(String jarName) {
		this.jarName = jarName;
	}

	public void handleEvent(Event event, boolean shouldLog) {
		SledgeHammer.instance.handle(event, shouldLog);
	}

	public void handleEvent(Event event) {
		SledgeHammer.instance.handle(event);
	}

	public Module getModuleByID(String ID) {
		return getModuleManager().getModuleByID(ID);
	}

	public boolean hasPermission(String username, String context) {
		return getPermissionsManager().hasPermission(username, context);
	}

	public PermissionsManager getPermissionsManager() {
		return SledgeHammer.instance.getPermissionsManager();
	}

	public void updateModule(long delta) {
		if (started)
			onUpdate(delta);
	}

	public boolean loadedSettings() {
		return this.loadedSettings;
	}

	public String getPublicServerName() {
		return SledgeHammer.instance.getPublicServerName();
	}

	public void register(LogListener listener) {
		SledgeHammer.instance.register(listener);
	}
	
	public void register(ExceptionListener listener) {
		SledgeHammer.instance.register(listener);
	}

	public void register(String type, EventListener listener) {
		SledgeHammer.instance.register(type, listener);
	}

	public void register(String command, CommandListener listener) {
		SledgeHammer.instance.register(command, listener);
	}

	public ChatManager getChatManager() {
		return SledgeHammer.instance.getChatManager();
	}

	public void sendGlobalMessage(String message) {
		ChatChannel global = getChatManager().getChannel("global");
		global.addMessage(message);
	}
	
//	public String warnPlayer(String commander, String username, String text) {
//		return getChatManager().warnPlayer(commander, username, text);
//	}
//
//	public String messagePlayer(String username, String header, String headerColor, String text, String textColor, boolean addTimeStamp, boolean bypassMute) {
//		return getChatManager().messagePlayer(username, header, headerColor, text, textColor, addTimeStamp, bypassMute);
//	}
//
//	public String messagePlayer(Player player, String header, String headerColor, String text, String textColor, boolean addTimeStamp, boolean bypassMute) {
//		return getChatManager().messagePlayer(player, header, headerColor, text, textColor, addTimeStamp, bypassMute);
//	}
//
//	public String privateMessage(String commander, String username, String text) {
//		return getChatManager().privateMessage(commander, username, text);
//	}
//
//	public String privateMessage(String commander, UdpConnection connection, String text) {
//		return getChatManager().privateMessage(commander, connection, text);
//	}
//	
//	public String warnPlayerDirty(String commander, String username, String text) {
//		return getChatManager().warnPlayerDirty(commander, username, text);
//	}
//
//	public String messagePlayerDirty(String username, String header, String headerColor, String text, String textColor, boolean addTimeStamp, boolean bypassMute) {
//		return getChatManager().messagePlayerDirty(username, header, headerColor, text, textColor, addTimeStamp, bypassMute);
//	}
//
//	public String privateMessageDirty(String commander, String username, String text) {
//		return getChatManager().privateMessageDirty(commander, username, text);
//	}
//
//	public void localMessage(UdpConnection connection, int playerID, String text, byte chatType, byte sayIt) {
//		getChatManager().localMessage(connection, playerID, text, chatType, sayIt);
//	}
//
//	public void messageGlobal(String message) {
//		getChatManager().messageGlobal(message);
//	}
//
//	public void messageGlobal(String header, String message) {
//		getChatManager().messageGlobal(header, message);
//	}
//
//	public void messageGlobal(String header, String headerColor, String message, String messageColor) {
//		getChatManager().messageGlobal(header, headerColor, message, messageColor);
//	}
//
//	public void messageGlobal(String header, String headerColor, String message, String messageColor,
//			boolean timeStamp) {
//		getChatManager().messageGlobal(header, headerColor, message, messageColor, timeStamp);
//	}
//
//	public void broadcastMessage(String message, String messageColor) {
//		getChatManager().broadcastMessage(message, messageColor);
//	}
	
	public List<Player> getPlayers() {
		return SledgeHammer.instance.getPlayers();
	}

	public boolean isLoaded() {
		return loaded;
	}

	/**
	 * Used to execute GenericEvent commands. This will be picked up by modules that @override this this.
	 * 
	 * @param type
	 * 
	 * @param context
	 */
	public void executeCommand(String type, String context) {
		
	}

	public abstract void onLoad();

	public abstract void onStart();

	public abstract void onUpdate(long delta);

	public abstract void onStop();

	public abstract void onUnload();

	public abstract String getID();

	public abstract String getVersion();
	
	public abstract String getModuleName();

	public abstract void onClientCommand(ClientEvent e);
	
}
