/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.prestosql.connector;

import com.google.common.collect.ImmutableList;
import io.prestosql.spi.connector.ColumnHandle;
import io.prestosql.spi.connector.ColumnMetadata;
import io.prestosql.spi.connector.Connector;
import io.prestosql.spi.connector.ConnectorAccessControl;
import io.prestosql.spi.connector.ConnectorInsertTableHandle;
import io.prestosql.spi.connector.ConnectorMetadata;
import io.prestosql.spi.connector.ConnectorNewTableLayout;
import io.prestosql.spi.connector.ConnectorOutputTableHandle;
import io.prestosql.spi.connector.ConnectorSession;
import io.prestosql.spi.connector.ConnectorSplitManager;
import io.prestosql.spi.connector.ConnectorTableHandle;
import io.prestosql.spi.connector.ConnectorTableMetadata;
import io.prestosql.spi.connector.ConnectorTableProperties;
import io.prestosql.spi.connector.ConnectorTransactionHandle;
import io.prestosql.spi.connector.ConnectorViewDefinition;
import io.prestosql.spi.connector.ProjectionApplicationResult;
import io.prestosql.spi.connector.SchemaTableName;
import io.prestosql.spi.connector.SchemaTablePrefix;
import io.prestosql.spi.connector.SortItem;
import io.prestosql.spi.connector.TopNApplicationResult;
import io.prestosql.spi.eventlistener.EventListener;
import io.prestosql.spi.expression.ConnectorExpression;
import io.prestosql.spi.security.PrestoPrincipal;
import io.prestosql.spi.security.Privilege;
import io.prestosql.spi.security.RoleGrant;
import io.prestosql.spi.transaction.IsolationLevel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;

public class MockConnector
        implements Connector
{
    private final Function<ConnectorSession, List<String>> listSchemaNames;
    private final BiFunction<ConnectorSession, String, List<SchemaTableName>> listTables;
    private final BiFunction<ConnectorSession, SchemaTablePrefix, Map<SchemaTableName, ConnectorViewDefinition>> getViews;
    private final BiFunction<ConnectorSession, SchemaTableName, ConnectorTableHandle> getTableHandle;
    private final Function<SchemaTableName, List<ColumnMetadata>> getColumns;
    private final MockConnectorFactory.ApplyProjection applyProjection;
    private final MockConnectorFactory.ApplyTopN applyTopN;
    private final BiFunction<ConnectorSession, SchemaTableName, Optional<ConnectorNewTableLayout>> getInsertLayout;
    private final BiFunction<ConnectorSession, ConnectorTableMetadata, Optional<ConnectorNewTableLayout>> getNewTableLayout;
    private final Supplier<Iterable<EventListener>> eventListeners;
    private final MockConnectorFactory.ListRoleGrants roleGrants;
    private final MockConnectorAccessControl accessControl;

    MockConnector(
            Function<ConnectorSession, List<String>> listSchemaNames,
            BiFunction<ConnectorSession, String, List<SchemaTableName>> listTables,
            BiFunction<ConnectorSession, SchemaTablePrefix, Map<SchemaTableName, ConnectorViewDefinition>> getViews,
            BiFunction<ConnectorSession, SchemaTableName, ConnectorTableHandle> getTableHandle,
            Function<SchemaTableName, List<ColumnMetadata>> getColumns,
            MockConnectorFactory.ApplyProjection applyProjection,
            MockConnectorFactory.ApplyTopN applyTopN,
            BiFunction<ConnectorSession, SchemaTableName, Optional<ConnectorNewTableLayout>> getInsertLayout,
            BiFunction<ConnectorSession, ConnectorTableMetadata, Optional<ConnectorNewTableLayout>> getNewTableLayout,
            Supplier<Iterable<EventListener>> eventListeners,
            MockConnectorFactory.ListRoleGrants roleGrants,
            MockConnectorAccessControl accessControl)
    {
        this.listSchemaNames = requireNonNull(listSchemaNames, "listSchemaNames is null");
        this.listTables = requireNonNull(listTables, "listTables is null");
        this.getViews = requireNonNull(getViews, "getViews is null");
        this.getTableHandle = requireNonNull(getTableHandle, "getTableHandle is null");
        this.getColumns = requireNonNull(getColumns, "getColumns is null");
        this.applyProjection = requireNonNull(applyProjection, "applyProjection is null");
        this.applyTopN = requireNonNull(applyTopN, "applyTopN is null");
        this.getInsertLayout = requireNonNull(getInsertLayout, "getInsertLayout is null");
        this.getNewTableLayout = requireNonNull(getNewTableLayout, "getNewTableLayout is null");
        this.eventListeners = requireNonNull(eventListeners, "eventListeners is null");
        this.roleGrants = requireNonNull(roleGrants, "roleGrants is null");
        this.accessControl = requireNonNull(accessControl, "accessControl is null");
    }

    @Override
    public ConnectorTransactionHandle beginTransaction(IsolationLevel isolationLevel, boolean readOnly)
    {
        return MockConnectorTransactionHandle.INSTANCE;
    }

    @Override
    public ConnectorMetadata getMetadata(ConnectorTransactionHandle transaction)
    {
        return new MockConnectorMetadata();
    }

    @Override
    public ConnectorSplitManager getSplitManager()
    {
        return new ConnectorSplitManager() {};
    }

    @Override
    public Iterable<EventListener> getEventListeners()
    {
        return eventListeners.get();
    }

    @Override
    public ConnectorAccessControl getAccessControl()
    {
        return accessControl;
    }

    private class MockConnectorMetadata
            implements ConnectorMetadata
    {
        @Override
        public boolean schemaExists(ConnectorSession session, String schemaName)
        {
            return listSchemaNames.apply(session).contains(schemaName);
        }

        @Override
        public Optional<ProjectionApplicationResult<ConnectorTableHandle>> applyProjection(ConnectorSession session, ConnectorTableHandle handle, List<ConnectorExpression> projections, Map<String, ColumnHandle> assignments)
        {
            return applyProjection.apply(session, handle, projections, assignments);
        }

        @Override
        public Optional<TopNApplicationResult<ConnectorTableHandle>> applyTopN(ConnectorSession session, ConnectorTableHandle handle, long topNCount, List<SortItem> sortItems, Map<String, ColumnHandle> assignments)
        {
            return applyTopN.apply(session, handle, topNCount, sortItems, assignments);
        }

        @Override
        public List<String> listSchemaNames(ConnectorSession session)
        {
            return listSchemaNames.apply(session);
        }

        @Override
        public ConnectorTableHandle getTableHandle(ConnectorSession session, SchemaTableName tableName)
        {
            return getTableHandle.apply(session, tableName);
        }

        @Override
        public ConnectorTableMetadata getTableMetadata(ConnectorSession session, ConnectorTableHandle tableHandle)
        {
            MockConnectorTableHandle table = (MockConnectorTableHandle) tableHandle;
            return new ConnectorTableMetadata(table.getTableName(), getColumns.apply(table.getTableName()));
        }

        @Override
        public List<SchemaTableName> listTables(ConnectorSession session, Optional<String> schemaName)
        {
            if (schemaName.isPresent()) {
                return listTables.apply(session, schemaName.get());
            }
            ImmutableList.Builder<SchemaTableName> tableNames = ImmutableList.builder();
            for (String schema : listSchemaNames(session)) {
                tableNames.addAll(listTables.apply(session, schema));
            }
            return tableNames.build();
        }

        @Override
        public Map<String, ColumnHandle> getColumnHandles(ConnectorSession session, ConnectorTableHandle tableHandle)
        {
            MockConnectorTableHandle table = (MockConnectorTableHandle) tableHandle;
            return getColumns.apply(table.getTableName()).stream()
                    .collect(toImmutableMap(ColumnMetadata::getName, column -> new MockConnectorColumnHandle(column.getName(), column.getType())));
        }

        @Override
        public ColumnMetadata getColumnMetadata(ConnectorSession session, ConnectorTableHandle tableHandle, ColumnHandle columnHandle)
        {
            MockConnectorColumnHandle mockColumnHandle = (MockConnectorColumnHandle) columnHandle;
            return new ColumnMetadata(mockColumnHandle.getName(), mockColumnHandle.getType());
        }

        @Override
        public Map<SchemaTableName, List<ColumnMetadata>> listTableColumns(ConnectorSession session, SchemaTablePrefix prefix)
        {
            return listTables(session, prefix.getSchema()).stream()
                    .filter(prefix::matches)
                    .collect(toImmutableMap(table -> table, getColumns));
        }

        @Override
        public Map<SchemaTableName, ConnectorViewDefinition> getViews(ConnectorSession session, Optional<String> schemaName)
        {
            return getViews.apply(session, schemaName.map(SchemaTablePrefix::new).orElseGet(SchemaTablePrefix::new));
        }

        @Override
        public Optional<ConnectorViewDefinition> getView(ConnectorSession session, SchemaTableName viewName)
        {
            return Optional.ofNullable(getViews.apply(session, viewName.toSchemaTablePrefix()).get(viewName));
        }

        @Override
        public ConnectorInsertTableHandle beginInsert(ConnectorSession session, ConnectorTableHandle tableHandle)
        {
            return new MockConnectorInsertTableHandle();
        }

        @Override
        public Optional<ConnectorNewTableLayout> getInsertLayout(ConnectorSession session, ConnectorTableHandle tableHandle)
        {
            MockConnectorTableHandle table = (MockConnectorTableHandle) tableHandle;
            return getInsertLayout.apply(session, table.getTableName());
        }

        @Override
        public ConnectorOutputTableHandle beginCreateTable(ConnectorSession session, ConnectorTableMetadata tableMetadata, Optional<ConnectorNewTableLayout> layout)
        {
            return new MockConnectorOutputTableHandle();
        }

        @Override
        public Optional<ConnectorNewTableLayout> getNewTableLayout(ConnectorSession session, ConnectorTableMetadata tableMetadata)
        {
            return getNewTableLayout.apply(session, tableMetadata);
        }

        @Override
        public boolean usesLegacyTableLayouts()
        {
            return false;
        }

        @Override
        public ConnectorTableProperties getTableProperties(ConnectorSession session, ConnectorTableHandle table)
        {
            return new ConnectorTableProperties();
        }

        @Override
        public Set<String> listRoles(ConnectorSession session)
        {
            return roleGrants.apply(session, Optional.empty(), Optional.empty(), OptionalLong.empty()).stream().map(grant -> grant.getRoleName()).collect(toImmutableSet());
        }

        @Override
        public Set<RoleGrant> listRoleGrants(ConnectorSession session, PrestoPrincipal principal)
        {
            return roleGrants.apply(session, Optional.empty(), Optional.empty(), OptionalLong.empty()).stream().filter(grant -> grant.getGrantee().equals(principal)).collect(toImmutableSet());
        }

        @Override
        public Set<RoleGrant> listAllRoleGrants(ConnectorSession session, Optional<Set<String>> roles, Optional<Set<String>> grantees, OptionalLong limit)
        {
            return roleGrants.apply(session, roles, grantees, limit);
        }

        @Override
        public Set<RoleGrant> listApplicableRoles(ConnectorSession session, PrestoPrincipal principal)
        {
            return listRoleGrants(session, principal);
        }

        @Override
        public Set<String> listEnabledRoles(ConnectorSession session)
        {
            return listRoles(session);
        }

        @Override
        public void grantSchemaPrivileges(ConnectorSession session, String schemaName, Set<Privilege> privileges, PrestoPrincipal grantee, boolean grantOption)
        {
            accessControl.grantSchemaPrivileges(schemaName, privileges, grantee, grantOption);
        }

        @Override
        public void revokeSchemaPrivileges(ConnectorSession session, String schemaName, Set<Privilege> privileges, PrestoPrincipal revokee, boolean grantOption)
        {
            accessControl.revokeSchemaPrivileges(schemaName, privileges, revokee, grantOption);
        }

        @Override
        public void grantTablePrivileges(ConnectorSession session, SchemaTableName tableName, Set<Privilege> privileges, PrestoPrincipal grantee, boolean grantOption)
        {
            accessControl.grantTablePrivileges(tableName, privileges, grantee, grantOption);
        }

        @Override
        public void revokeTablePrivileges(ConnectorSession session, SchemaTableName tableName, Set<Privilege> privileges, PrestoPrincipal revokee, boolean grantOption)
        {
            accessControl.revokeTablePrivileges(tableName, privileges, revokee, grantOption);
        }
    }
}
