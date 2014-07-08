package org.lobobrowser.security;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.javatuples.Pair;
import org.lobobrowser.security.PermissionSystem.Permission;
import org.lobobrowser.ua.UserAgentContext.RequestKind;

interface RequestRuleStore {
  public Pair<Permission, Permission[]> getPermissions(final String frameHostPattern, final String requestHost);

  public void storePermissions(final String frameHost, final String requestHost, Optional<RequestKind> kindOpt, Permission permission);

  public static RequestRuleStore getStore() {
    return InMemoryStore.getInstance();
  }

  static class HelperPrivate {
    static void initStore(RequestRuleStore store) {
      final Pair<Permission, Permission[]> permissions = store.getPermissions("*", "");
      assert (!permissions.getValue0().isDecided());
      store.storePermissions("*", "", Optional.empty(), Permission.Deny);
      store.storePermissions("*", "", Optional.of(RequestKind.Image), Permission.Allow);
      store.storePermissions("*", "", Optional.of(RequestKind.CSS), Permission.Allow);
    }
  }

  static class InMemoryStore implements RequestRuleStore {
    private Map<String, Map<String, Permission[]>> store = new HashMap<>();
    private static final Permission[] defaultPermissions = new Permission[RequestKind.numKinds() + 1];
    static {
      for (int i = 0; i < defaultPermissions.length; i++) {
        defaultPermissions[i] = Permission.Undecided;
      }
    }
    private static final Pair<Permission, Permission[]> defaultPermissionPair = Pair.with(Permission.Undecided, defaultPermissions);

    static private InMemoryStore instance = new InMemoryStore();

    public static RequestRuleStore getInstance() {
      instance.dump();
      return instance;
    }

    public InMemoryStore() {
      HelperPrivate.initStore(this);
      /*
      try (ObjectInputStream os = new ObjectInputStream(new FileInputStream(new File(FILE_NAME)))){
        store = (Map<String, Map<String, Permission[]>>) os.readObject();
      } catch (FileNotFoundException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (ClassNotFoundException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }*/
    }

    public synchronized Pair<Permission, Permission[]> getPermissions(final String frameHostPattern, final String requestHost) {
      final Map<String, Permission[]> reqHostMap = store.get(frameHostPattern);
      if (reqHostMap != null) {
        final Permission[] permissions = reqHostMap.get(requestHost);
        if (permissions != null) {
          return Pair.with(permissions[0], Arrays.copyOfRange(permissions, 1, permissions.length));
        } else {
          return defaultPermissionPair;
        }
      } else {
        return defaultPermissionPair;
      }
    }

    public synchronized void storePermissions(final String frameHostPattern, final String requestHost, final Optional<RequestKind> kindOpt,
        final Permission permission) {
      final int index = kindOpt.map(k -> k.ordinal() + 1).orElse(0);
      final Map<String, Permission[]> reqHostMap = store.get(frameHostPattern);
      if (reqHostMap != null) {
        final Permission[] permissions = reqHostMap.get(requestHost);
        if (permissions != null) {
          permissions[index] = permission;
        } else {
          addPermission(requestHost, index, permission, reqHostMap);
        }
      } else {
        final Map<String, Permission[]> newReqHostMap = new HashMap<>();
        addPermission(requestHost, index, permission, newReqHostMap);
        store.put(frameHostPattern, newReqHostMap);
      }

      /*
      try {
        final ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(new File(FILE_NAME)));
        os.writeObject(store);
        os.close();
      } catch (final FileNotFoundException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (final IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }*/
    }

    private static void addPermission(final String requestHost, final int index, final Permission permission,
        final Map<String, Permission[]> reqHostMap) {
      final Permission[] newPermissions = Arrays.copyOf(defaultPermissions, defaultPermissions.length);
      newPermissions[index] = permission;
      reqHostMap.put(requestHost, newPermissions);
    }

    void dump() {
      System.out.println("Store: ");
      store.forEach((key, value) -> {
        System.out.println("{" + key + ": ");
        value.forEach((key2, value2) -> {
          System.out.println("  " + key2 + ": " + Arrays.toString(value2));
        });
        System.out.println("}");
      });
    }
  }
}
