package org.lobobrowser.security;

import info.gngr.db.tables.Permissions;
import info.gngr.db.tables.records.PermissionsRecord;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.javatuples.Pair;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.lobobrowser.security.PermissionSystem.Permission;
import org.lobobrowser.store.StorageManager;
import org.lobobrowser.ua.UserAgentContext.RequestKind;

interface RequestRuleStore {
  public Pair<Permission, Permission[]> getPermissions(final String frameHostPattern, final String requestHost);

  public void storePermissions(final String frameHost, final String requestHost, Optional<RequestKind> kindOpt, Permission permission);

  public static RequestRuleStore getStore() {
    // return InMemoryStore.getInstance();
    return DBStore.getInstance();
  }

  static class HelperPrivate {
    static void initStore(final RequestRuleStore store) {
      final Pair<Permission, Permission[]> permissions = store.getPermissions("*", "");
      assert (!permissions.getValue0().isDecided());
      store.storePermissions("*", "", Optional.empty(), Permission.Deny);
      store.storePermissions("*", "", Optional.of(RequestKind.Image), Permission.Allow);
      store.storePermissions("*", "", Optional.of(RequestKind.CSS), Permission.Allow);
    }
  }

  static class InMemoryStore implements RequestRuleStore {
    private final Map<String, Map<String, Permission[]>> store = new HashMap<>();
    private static final Permission[] defaultPermissions = new Permission[RequestKind.numKinds() + 1];
    static {
      for (int i = 0; i < defaultPermissions.length; i++) {
        defaultPermissions[i] = Permission.Undecided;
      }
    }
    private static final Pair<Permission, Permission[]> defaultPermissionPair = Pair.with(Permission.Undecided, defaultPermissions);

    static private InMemoryStore instance = new InMemoryStore();

    public static InMemoryStore getInstance() {
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

  static class DBStore implements RequestRuleStore {
    private final DSLContext userDB;
    private static final Permission[] defaultPermissions = new Permission[RequestKind.numKinds()];
    static {
      for (int i = 0; i < defaultPermissions.length; i++) {
        defaultPermissions[i] = Permission.Undecided;
      }
    }
    private static final Pair<Permission, Permission[]> defaultPermissionPair = Pair.with(Permission.Undecided, defaultPermissions);

    static private DBStore instance = new DBStore();

    public static DBStore getInstance() {
      return instance;
    }

    public DBStore() {
      try {
        userDB = StorageManager.getInstance().userDB;
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
      HelperPrivate.initStore(this);
    }

    private static Condition matchHostsCondition(final String frameHost, final String requestHost) {
      return Permissions.PERMISSIONS.FRAMEHOST.equal(frameHost).and(Permissions.PERMISSIONS.REQUESTHOST.equal(requestHost));
    }

    public Pair<Permission, Permission[]> getPermissions(final String frameHostPattern, final String requestHost) {
      final Result<PermissionsRecord> permissionRecords = AccessController.doPrivileged((PrivilegedAction<Result<PermissionsRecord>>)() -> {
          return userDB.fetch(Permissions.PERMISSIONS, matchHostsCondition(frameHostPattern, requestHost));
      });

      if (permissionRecords.isEmpty()) {
        return defaultPermissionPair;
      } else {
        final PermissionsRecord existingRecord = permissionRecords.get(0);
        final Integer existingPermissions = existingRecord.getPermissions();
        final Pair<Permission, Permission[]> permissions = decodeBitMask(existingPermissions);
        return permissions;
      }
    }

    private static Pair<Permission, Permission[]> decodeBitMask(final Integer existingPermissions) {
      final Permission[] resultPermissions = new Permission[RequestKind.numKinds()];
      for (int i = 0; i < resultPermissions.length; i++) {
        resultPermissions[i] = decodeBits(existingPermissions, i+1);
      }
      final Pair<Permission, Permission[]> resultPair = Pair.with(decodeBits(existingPermissions, 0), resultPermissions);
      return resultPair;
    }

    private static final int BITS_PER_KIND = 2;

    private static Permission decodeBits(final Integer existingPermissions, final int i) {
      final int permissionBits = (existingPermissions >> (i * BITS_PER_KIND)) & 0x3;
      if (permissionBits < 2) {
        return Permission.Undecided;
      } else {
        return permissionBits == 0x3 ? Permission.Allow : Permission.Deny;
      }
    }

    public void storePermissions(final String frameHost, final String requestHost, final Optional<RequestKind> kindOpt,
        final Permission permission) {
      final Result<PermissionsRecord> permissionRecords = AccessController.doPrivileged((PrivilegedAction<Result<PermissionsRecord>>)() -> {
          return userDB.fetch(Permissions.PERMISSIONS, matchHostsCondition(frameHost, requestHost));
      });

      final Integer permissionMask = makeBitMask(kindOpt, permission);

      if (permissionRecords.isEmpty()) {
        final PermissionsRecord newPermissionRecord = new PermissionsRecord(frameHost, requestHost, permissionMask);
        newPermissionRecord.attach(userDB.configuration());
        newPermissionRecord.store();
      } else {
        final PermissionsRecord existingRecord = permissionRecords.get(0);
        final Integer existingPermissions = existingRecord.getPermissions();
        final int newPermissions = existingPermissions | permissionMask;
        existingRecord.setPermissions(newPermissions);
        existingRecord.store();
      }
    }

    private static Integer makeBitMask(final Optional<RequestKind> kindOpt, final Permission permission) {
      if (permission.isDecided()) {
        final Integer bitPos = kindOpt.map(k -> k.ordinal() + 1).orElse(0) * BITS_PER_KIND;
        final int bitset = permission == Permission.Allow ? 0x3 : 0x2;
        return bitset << bitPos;
      } else {
        return 0;
      }
    }
  }
}
