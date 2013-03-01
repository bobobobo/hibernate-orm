/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2011, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.metamodel.internal;

import static org.hibernate.engine.spi.SyntheticAttributeHelper.SYNTHETIC_COMPOSITE_ID_ATTRIBUTE_NAME;
import static org.hibernate.cfg.ObjectNameNormalizer.NamingStrategyHelper;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.hibernate.AssertionFailure;
import org.hibernate.EntityMode;
import org.hibernate.MultiTenancyStrategy;
import org.hibernate.TruthValue;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.NotYetImplementedException;
import org.hibernate.cfg.ObjectNameNormalizer;
import org.hibernate.engine.FetchStyle;
import org.hibernate.engine.FetchTiming;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.engine.spi.CascadeStyle;
import org.hibernate.engine.spi.CascadeStyles;
import org.hibernate.engine.spi.FilterDefinition;
import org.hibernate.id.EntityIdentifierNature;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.id.IdentityGenerator;
import org.hibernate.id.PersistentIdentifierGenerator;
import org.hibernate.id.factory.IdentifierGeneratorFactory;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.internal.FilterConfiguration;
import org.hibernate.internal.util.ReflectHelper;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.internal.util.ValueHolder;
import org.hibernate.internal.util.collections.CollectionHelper;
import org.hibernate.metamodel.internal.HibernateTypeHelper.ReflectedCollectionJavaTypes;
import org.hibernate.metamodel.spi.MetadataImplementor;
import org.hibernate.metamodel.spi.binding.AbstractPluralAttributeBinding;
import org.hibernate.metamodel.spi.binding.AttributeBinding;
import org.hibernate.metamodel.spi.binding.AttributeBindingContainer;
import org.hibernate.metamodel.spi.binding.BasicAttributeBinding;
import org.hibernate.metamodel.spi.binding.BasicPluralAttributeElementBinding;
import org.hibernate.metamodel.spi.binding.BasicPluralAttributeIndexBinding;
import org.hibernate.metamodel.spi.binding.Cascadeable;
import org.hibernate.metamodel.spi.binding.CompositeAttributeBinding;
import org.hibernate.metamodel.spi.binding.CompositeAttributeBindingContainer;
import org.hibernate.metamodel.spi.binding.CompositePluralAttributeElementBinding;
import org.hibernate.metamodel.spi.binding.CompositePluralAttributeIndexBinding;
import org.hibernate.metamodel.spi.binding.EntityBinding;
import org.hibernate.metamodel.spi.binding.EntityDiscriminator;
import org.hibernate.metamodel.spi.binding.EntityIdentifier;
import org.hibernate.metamodel.spi.binding.EntityVersion;
import org.hibernate.metamodel.spi.binding.HibernateTypeDescriptor;
import org.hibernate.metamodel.spi.binding.IdGenerator;
import org.hibernate.metamodel.spi.binding.IndexedPluralAttributeBinding;
import org.hibernate.metamodel.spi.binding.InheritanceType;
import org.hibernate.metamodel.spi.binding.ManyToManyPluralAttributeElementBinding;
import org.hibernate.metamodel.spi.binding.ManyToOneAttributeBinding;
import org.hibernate.metamodel.spi.binding.MetaAttribute;
import org.hibernate.metamodel.spi.binding.OneToManyPluralAttributeElementBinding;
import org.hibernate.metamodel.spi.binding.OneToOneAttributeBinding;
import org.hibernate.metamodel.spi.binding.PluralAttributeBinding;
import org.hibernate.metamodel.spi.binding.PluralAttributeElementBinding;
import org.hibernate.metamodel.spi.binding.PluralAttributeIndexBinding;
import org.hibernate.metamodel.spi.binding.PluralAttributeKeyBinding;
import org.hibernate.metamodel.spi.binding.RelationalValueBinding;
import org.hibernate.metamodel.spi.binding.SecondaryTable;
import org.hibernate.metamodel.spi.binding.SetBinding;
import org.hibernate.metamodel.spi.binding.SingularAssociationAttributeBinding;
import org.hibernate.metamodel.spi.binding.SingularAttributeBinding;
import org.hibernate.metamodel.spi.binding.SingularAttributeBinding.NaturalIdMutability;
import org.hibernate.metamodel.spi.binding.SingularNonAssociationAttributeBinding;
import org.hibernate.metamodel.spi.domain.Aggregate;
import org.hibernate.metamodel.spi.domain.Attribute;
import org.hibernate.metamodel.spi.domain.Entity;
import org.hibernate.metamodel.spi.domain.IndexedPluralAttribute;
import org.hibernate.metamodel.spi.domain.PluralAttribute;
import org.hibernate.metamodel.spi.domain.SingularAttribute;
import org.hibernate.metamodel.spi.relational.Column;
import org.hibernate.metamodel.spi.relational.DerivedValue;
import org.hibernate.metamodel.spi.relational.ForeignKey;
import org.hibernate.metamodel.spi.relational.Identifier;
import org.hibernate.metamodel.spi.relational.PrimaryKey;
import org.hibernate.metamodel.spi.relational.Schema;
import org.hibernate.metamodel.spi.relational.Table;
import org.hibernate.metamodel.spi.relational.TableSpecification;
import org.hibernate.metamodel.spi.relational.UniqueKey;
import org.hibernate.metamodel.spi.relational.Value;
import org.hibernate.metamodel.spi.source.AggregatedCompositeIdentifierSource;
import org.hibernate.metamodel.spi.source.AttributeSource;
import org.hibernate.metamodel.spi.source.AttributeSourceContainer;
import org.hibernate.metamodel.spi.source.BasicPluralAttributeElementSource;
import org.hibernate.metamodel.spi.source.BasicPluralAttributeIndexSource;
import org.hibernate.metamodel.spi.source.ColumnSource;
import org.hibernate.metamodel.spi.source.ComponentAttributeSource;
import org.hibernate.metamodel.spi.source.CompositePluralAttributeElementSource;
import org.hibernate.metamodel.spi.source.CompositePluralAttributeIndexSource;
import org.hibernate.metamodel.spi.source.ConstraintSource;
import org.hibernate.metamodel.spi.source.DerivedValueSource;
import org.hibernate.metamodel.spi.source.DiscriminatorSource;
import org.hibernate.metamodel.spi.source.EntityHierarchy;
import org.hibernate.metamodel.spi.source.EntitySource;
import org.hibernate.metamodel.spi.source.FilterSource;
import org.hibernate.metamodel.spi.source.ForeignKeyContributingSource;
import org.hibernate.metamodel.spi.source.ForeignKeyContributingSource.JoinColumnResolutionContext;
import org.hibernate.metamodel.spi.source.ForeignKeyContributingSource.JoinColumnResolutionDelegate;
import org.hibernate.metamodel.spi.source.IdentifierSource;
import org.hibernate.metamodel.spi.source.InLineViewSource;
import org.hibernate.metamodel.spi.source.IndexedPluralAttributeSource;
import org.hibernate.metamodel.spi.source.JoinedSubclassEntitySource;
import org.hibernate.metamodel.spi.source.PluralAttributeElementSourceResolver;
import org.hibernate.metamodel.spi.source.PluralAttributeIndexSource;
import org.hibernate.metamodel.spi.source.LocalBindingContext;
import org.hibernate.metamodel.spi.source.ManyToManyPluralAttributeElementSource;
import org.hibernate.metamodel.spi.source.MappingDefaults;
import org.hibernate.metamodel.spi.source.MetaAttributeContext;
import org.hibernate.metamodel.spi.source.MetaAttributeSource;
import org.hibernate.metamodel.spi.source.MultiTenancySource;
import org.hibernate.metamodel.spi.source.NonAggregatedCompositeIdentifierSource;
import org.hibernate.metamodel.spi.source.OneToManyPluralAttributeElementSource;
import org.hibernate.metamodel.spi.source.Orderable;
import org.hibernate.metamodel.spi.source.PluralAttributeElementSource;
import org.hibernate.metamodel.spi.source.PluralAttributeKeySource;
import org.hibernate.metamodel.spi.source.PluralAttributeSource;
import org.hibernate.metamodel.spi.source.RelationalValueSource;
import org.hibernate.metamodel.spi.source.RelationalValueSourceContainer;
import org.hibernate.metamodel.spi.source.RootEntitySource;
import org.hibernate.metamodel.spi.source.SecondaryTableSource;
import org.hibernate.metamodel.spi.source.SequentialPluralAttributeIndexSource;
import org.hibernate.metamodel.spi.source.SimpleIdentifierSource;
import org.hibernate.metamodel.spi.source.SingularAttributeSource;
import org.hibernate.metamodel.spi.source.Sortable;
import org.hibernate.metamodel.spi.source.SubclassEntitySource;
import org.hibernate.metamodel.spi.source.TableSource;
import org.hibernate.metamodel.spi.source.TableSpecificationSource;
import org.hibernate.metamodel.spi.source.ToOneAttributeSource;
import org.hibernate.metamodel.spi.source.UniqueConstraintSource;
import org.hibernate.metamodel.spi.source.VersionAttributeSource;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.tuple.component.ComponentMetamodel;
import org.hibernate.tuple.entity.EntityTuplizer;
import org.hibernate.type.ComponentType;
import org.hibernate.type.EntityType;
import org.hibernate.type.ForeignKeyDirection;
import org.hibernate.type.Type;

import org.jboss.logging.Logger;

/**
 * The common binder shared between annotations and {@code hbm.xml} processing.
 * <p/>
 * The API consists of {@link #Binder(org.hibernate.metamodel.spi.MetadataImplementor, IdentifierGeneratorFactory)} and {@link #bindEntityHierarchies}
 *
 * @author Steve Ebersole
 * @author Hardy Ferentschik
 * @author Gail Badner
 * @author Brett Meyer
 * @author Strong Liu
 */
public class Binder {
	private static final CoreMessageLogger log = Logger.getMessageLogger(
			CoreMessageLogger.class,
			Binder.class.getName()
	);

	private final MetadataImplementor metadata;
	private final IdentifierGeneratorFactory identifierGeneratorFactory;
	private final ObjectNameNormalizer nameNormalizer;

	// sources should be available throughout the binding process
	private final Map<String, EntitySource> entitySourcesByName = new HashMap<String, EntitySource>();
	private final Map<RootEntitySource, EntityHierarchy> entityHierarchiesByRootEntitySource =
				new LinkedHashMap<RootEntitySource, EntityHierarchy>();
	private final Map<String, AttributeSource> attributeSourcesByName = new HashMap<String, AttributeSource>();

	// todo : apply org.hibernate.metamodel.MetadataSources.getExternalCacheRegionDefinitions()
	// the inheritanceTypes and entityModes correspond with bindingContexts
	private final LinkedList<LocalBindingContext> bindingContexts = new LinkedList<LocalBindingContext>();
	private final LinkedList<InheritanceType> inheritanceTypes = new LinkedList<InheritanceType>();
	private final LinkedList<EntityMode> entityModes = new LinkedList<EntityMode>();

	// helpers
	private final HibernateTypeHelper typeHelper; // todo: refactor helper and remove redundant methods in this class
	private final ForeignKeyHelper foreignKeyHelper;

	public Binder(final MetadataImplementor metadata,
				  final IdentifierGeneratorFactory identifierGeneratorFactory) {
		this.metadata = metadata;
		this.identifierGeneratorFactory = identifierGeneratorFactory;
		this.typeHelper = new HibernateTypeHelper( this, metadata );
		this.foreignKeyHelper = new ForeignKeyHelper( this );
		this.nameNormalizer = metadata.getObjectNameNormalizer();
	}

	/**
	 * The entry point of {@linkplain Binder} class, adds all the entity hierarchy one by one.
	 *
	 * @param entityHierarchies The entity hierarchies resolved from mappings
	 */
	public void addEntityHierarchies(final Iterable<EntityHierarchy> entityHierarchies) {
		inheritanceTypes.clear();
		entityModes.clear();
		bindingContexts.clear();
		// Index sources by name so we can find and resolve entities on the fly as references to them
		// are encountered (e.g., within associations)
		for ( final EntityHierarchy entityHierarchy : entityHierarchies ) {
			entityHierarchiesByRootEntitySource.put( entityHierarchy.getRootEntitySource(), entityHierarchy );
			mapSourcesByName( entityHierarchy.getRootEntitySource() );
			addEntityBindings( entityHierarchy );
		}
	}

	private void addEntityBindings(final EntityHierarchy entityHierarchy) {
		final LocalBindingContextExecutor createEntityExecutor = new LocalBindingContextExecutor() {
			@Override
			public void execute(LocalBindingContextExecutionContext executorContext) {
				createEntityBinding( executorContext.getSuperEntityBinding(), executorContext.getEntitySource() );
			}
		};
		applyToEntityHierarchy( entityHierarchy, createEntityExecutor, createEntityExecutor );
	}

	public void bindEntityHierarchies() {
		bindingContexts.clear();
		inheritanceTypes.clear();
		entityModes.clear();

		LocalBindingContextExecutor rootEntityCallback = new LocalBindingContextExecutor() {
			@Override
			public void execute(LocalBindingContextExecutionContext bindingContextContext) {
				final RootEntitySource rootEntitySource = (RootEntitySource) bindingContextContext.getEntitySource();
				final EntityBinding rootEntityBinding = bindingContextContext.getEntityBinding();
				bindPrimaryTable( rootEntityBinding, rootEntitySource );
				// Create/Bind root-specific information
				bindIdentifier( rootEntityBinding, rootEntitySource.getIdentifierSource() );
				bindSecondaryTables( rootEntityBinding, rootEntitySource );
				bindVersion( rootEntityBinding, rootEntitySource.getVersioningAttributeSource() );
				bindDiscriminator( rootEntityBinding, rootEntitySource );
				bindIdentifierGenerator( rootEntityBinding );
				bindMultiTenancy( rootEntityBinding, rootEntitySource );
				rootEntityBinding.getHierarchyDetails().setCaching( rootEntitySource.getCaching() );
				rootEntityBinding.getHierarchyDetails().setNaturalIdCaching( rootEntitySource.getNaturalIdCaching() );
				rootEntityBinding.getHierarchyDetails()
								.setExplicitPolymorphism( rootEntitySource.isExplicitPolymorphism() );
				rootEntityBinding.getHierarchyDetails().setOptimisticLockStyle( rootEntitySource.getOptimisticLockStyle() );
				rootEntityBinding.setMutable( rootEntitySource.isMutable() );
				rootEntityBinding.setWhereFilter( rootEntitySource.getWhere() );
				rootEntityBinding.setRowId( rootEntitySource.getRowId() );
			}
		};
		LocalBindingContextExecutor subEntityCallback = new LocalBindingContextExecutor() {
			@Override
			public void execute(LocalBindingContextExecutionContext bindingContextContext) {
				final EntitySource entitySource = bindingContextContext.getEntitySource();
				final EntityBinding entityBinding = bindingContextContext.getEntityBinding();
				final EntityBinding superEntityBinding = bindingContextContext.getSuperEntityBinding();
				entityBinding.setMutable( entityBinding.getHierarchyDetails().getRootEntityBinding().isMutable() );
				markSuperEntityTableAbstractIfNecessary( superEntityBinding );
				bindPrimaryTable( entityBinding, entitySource );
				bindSubEntityPrimaryKey( entityBinding, entitySource );
				bindSecondaryTables( entityBinding, entitySource );
			}
		};
		Set<EntityHierarchy> unresolvedEntityHierarchies = new HashSet<EntityHierarchy>( );
		for ( final EntityHierarchy entityHierarchy : entityHierarchiesByRootEntitySource.values() ) {
			if ( isIdentifierDependentOnOtherEntityHierarchy( entityHierarchy ) ) {
				unresolvedEntityHierarchies.add( entityHierarchy );
			}
			else {
				applyToEntityHierarchy( entityHierarchy, rootEntityCallback, subEntityCallback );
			}
		}

		for ( EntityHierarchy entityHierarchy : unresolvedEntityHierarchies ) {
			applyToEntityHierarchy( entityHierarchy, rootEntityCallback, subEntityCallback );
		}

		for ( final EntityHierarchy entityHierarchy : entityHierarchiesByRootEntitySource.values() ) {
			createCompositeAttributes( entityHierarchy );
		}

		for ( final EntityHierarchy entityHierarchy : entityHierarchiesByRootEntitySource.values() ) {
			bindSingularAttributes( entityHierarchy, SingularAttributeSource.Nature.BASIC );
		}

		// do many-to-one before one-to-one
		for ( final EntityHierarchy entityHierarchy : entityHierarchiesByRootEntitySource.values() ) {
			bindSingularAttributes( entityHierarchy, SingularAttributeSource.Nature.MANY_TO_ONE );
		}

		for ( final EntityHierarchy entityHierarchy : entityHierarchiesByRootEntitySource.values() ) {
			bindSingularAttributes( entityHierarchy, SingularAttributeSource.Nature.ONE_TO_ONE );
		}

		for ( final EntityHierarchy entityHierarchy : entityHierarchiesByRootEntitySource.values() ) {
			bindPluralAttributes( entityHierarchy, false );
		}

		for ( final EntityHierarchy entityHierarchy : entityHierarchiesByRootEntitySource.values() ) {
			bindPluralAttributes( entityHierarchy, true );
		}

		LocalBindingContextExecutor uniqueKeyExecutor = new LocalBindingContextExecutor() {
			@Override
			public void execute(LocalBindingContextExecutionContext bindingContextContext) {
				bindUniqueConstraints( bindingContextContext.getEntityBinding(), bindingContextContext.getEntitySource() );
			}
		};
		for ( final EntityHierarchy entityHierarchy : entityHierarchiesByRootEntitySource.values() ) {
			applyToEntityHierarchy( entityHierarchy, uniqueKeyExecutor, uniqueKeyExecutor );
		}

		// TODO: check if any many-to-one attribute bindings with logicalOneToOne == false have all columns
		//       (and no formulas) contained in a defined unique key that only contains these columns.
		//       if so, mark the many-to-one as a logical one-to-one.

	}

	private boolean isIdentifierDependentOnOtherEntityHierarchy(EntityHierarchy entityHierarchy) {
		final RootEntitySource rootEntitySource = entityHierarchy.getRootEntitySource();
		final IdentifierSource identifierSource = rootEntitySource.getIdentifierSource();
		if ( identifierSource.getNature() != EntityIdentifierNature.SIMPLE ) {
			List<? extends AttributeSource> subAttributeSources =
					identifierSource.getNature() == EntityIdentifierNature.AGGREGATED_COMPOSITE ?
							( (AggregatedCompositeIdentifierSource) identifierSource ).getIdentifierAttributeSource().attributeSources() :
							( (NonAggregatedCompositeIdentifierSource) identifierSource ).getAttributeSourcesMakingUpIdentifier();
			return containsSingularAssociation( subAttributeSources );
		}
		else {
			return false;
		}
	}

	private boolean containsSingularAssociation(List<? extends AttributeSource> subAttributeSources) {
		for ( AttributeSource attributeSource : subAttributeSources ) {
			SingularAttributeSource singularAttributeSource = (SingularAttributeSource) attributeSource;
			if ( singularAttributeSource.getNature() == SingularAttributeSource.Nature.MANY_TO_ONE ||
					singularAttributeSource.getNature() == SingularAttributeSource.Nature.ONE_TO_ONE ) {
				return true;
			}
			else if ( ( (SingularAttributeSource) attributeSource ).getNature() == SingularAttributeSource.Nature.COMPOSITE ) {
				ComponentAttributeSource componentAttributeSource = (ComponentAttributeSource) attributeSource;
				return containsSingularAssociation( componentAttributeSource.attributeSources() );
			}
		}
		return false;
	}

	private void createCompositeAttributes(
			final EntityHierarchy entityHierarchy) {
		LocalBindingContextExecutor executor = new LocalBindingContextExecutor() {
			@Override
			public void execute(LocalBindingContextExecutionContext bindingContextContext) {
				createCompositeAttributes(
						bindingContextContext.getEntityBinding(),
						bindingContextContext.getEntitySource()
				);
			}
		};
		applyToEntityHierarchy( entityHierarchy, executor, executor );
	}

	private void createCompositeAttributes(
			final AttributeBindingContainer attributeBindingContainer,
			final AttributeSourceContainer attributeSourceContainer) {
		for ( AttributeSource attributeSource : attributeSourceContainer.attributeSources() ) {
			if ( attributeSource.isSingular() ) {
				SingularAttributeSource singularAttributeSource = (SingularAttributeSource) attributeSource;
				if (  singularAttributeSource.getNature() == SingularAttributeSource.Nature.COMPOSITE ) {
					ComponentAttributeSource componentAttributeSource = (ComponentAttributeSource) attributeSource;
					CompositeAttributeBinding compositeAttributeBinding =
							createAggregatedCompositeAttribute( attributeBindingContainer, componentAttributeSource, null );
					createCompositeAttributes( compositeAttributeBinding, componentAttributeSource );
				}
			}
		}
	}

	private void bindSingularAttributes(
			final EntityHierarchy entityHierarchy,
			final SingularAttributeSource.Nature nature) {
		LocalBindingContextExecutor executor = new LocalBindingContextExecutor() {
			@Override
			public void execute(LocalBindingContextExecutionContext bindingContextContext) {
				bindSingularAttributes(
						bindingContextContext.getEntityBinding(),
						bindingContextContext.getEntitySource(),
						nature,
						null
				);
			}
		};
		applyToEntityHierarchy( entityHierarchy, executor, executor );
	}

	private void bindSingularAttributes(
			final AttributeBindingContainer attributeBindingContainer,
			final AttributeSourceContainer attributeSourceContainer,
			final SingularAttributeSource.Nature nature,
			final SingularAttribute parentReference) {
		for ( AttributeSource attributeSource : attributeSourceContainer.attributeSources() ) {
			if ( parentReference != null && parentReference.getName().equals( attributeSource.getName() ) ) {
				// skip the attribute because it is the parent reference
				continue;
			}
			if ( attributeSource.isSingular() ) {
				SingularAttributeSource singularAttributeSource = (SingularAttributeSource) attributeSource;
				if ( singularAttributeSource.getNature() == SingularAttributeSource.Nature.COMPOSITE ) {
					final ComponentAttributeSource compositeAttributeSource = (ComponentAttributeSource) attributeSource;
					final CompositeAttributeBinding compositeAttributeBinding =
							(CompositeAttributeBinding) attributeBindingContainer.locateAttributeBinding( attributeSource.getName() );
					bindSingularAttributes(
							compositeAttributeBinding,
							compositeAttributeSource,
							nature,
							compositeAttributeBinding.getParentReference()
					);
					completeCompositeAttributeBindingIfPossible( compositeAttributeBinding, compositeAttributeSource );
				}
				else if ( singularAttributeSource.getNature() == nature ) {
					bindAttribute( attributeBindingContainer, attributeSource );
				}
			}
		}
	}

	private void completeCompositeAttributeBindingIfPossible(
			CompositeAttributeBinding compositeAttributeBinding,
			ComponentAttributeSource compositeAttributeSource
	) {
		final int nAttributeSourcesExcludingParent =
				compositeAttributeBinding.getParentReference() != null ?
						compositeAttributeSource.attributeSources().size() - 1 :
						compositeAttributeSource.attributeSources().size();
		if ( compositeAttributeBinding.attributeBindingSpan() == nAttributeSourcesExcludingParent ) {
			boolean allResolved = true;
			for ( AttributeBinding attributeBinding : compositeAttributeBinding.attributeBindings() ) {
				if ( attributeBinding.getHibernateTypeDescriptor().getResolvedTypeMapping() == null ) {
					allResolved = false;
					break;
				}
			}
			if ( allResolved ) {
				typeHelper.bindAggregatedCompositeAttributeType(
						false,
						(Aggregate) compositeAttributeBinding.getAttribute().getSingularAttributeType(),
						null, // TODO: don't have the default value at this point; shouldn't be needed...
						compositeAttributeBinding
				);
			}
		}
	}

	private void bindPluralAttributes(
			final EntityHierarchy entityHierarchy,
			final boolean isInverse) {
		LocalBindingContextExecutor executor = new LocalBindingContextExecutor() {
			@Override
			public void execute(LocalBindingContextExecutionContext bindingContextContext) {
				bindPluralAttributes(
						bindingContextContext.getEntityBinding(),
						bindingContextContext.getEntitySource(),
						isInverse
				);
			}
		};
		applyToEntityHierarchy( entityHierarchy, executor, executor );
	}

	private void bindPluralAttributes(
			final AttributeBindingContainer attributeBindingContainer,
			final AttributeSourceContainer attributeSourceContainer,
			final boolean isInverse) {
		for ( AttributeSource attributeSource : attributeSourceContainer.attributeSources() ) {
			if ( attributeSource.isSingular() ) {
				SingularAttributeSource singularAttributeSource = (SingularAttributeSource) attributeSource;
				if ( singularAttributeSource.getNature() == SingularAttributeSource.Nature.COMPOSITE ) {
					final ComponentAttributeSource compositeAttributeSource = (ComponentAttributeSource) attributeSource;
					final CompositeAttributeBinding compositeAttributeBinding =
							(CompositeAttributeBinding) attributeBindingContainer.locateAttributeBinding( attributeSource.getName() );
					bindPluralAttributes(
							compositeAttributeBinding,
							compositeAttributeSource,
							isInverse
					);
					completeCompositeAttributeBindingIfPossible( compositeAttributeBinding, compositeAttributeSource );
				}
			}
			else {
				if ( isInverse == ( (PluralAttributeSource) attributeSource ).isInverse() ) {
					bindAttribute( attributeBindingContainer, attributeSource );
				}
			}
		}
	}

	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ Entity binding relates methods

	private EntityBinding findOrBindEntityBinding(
			final ValueHolder<Class<?>> entityJavaTypeValue,
			final String explicitEntityName) {
		final String referencedEntityName =
				explicitEntityName != null
						? explicitEntityName
						: entityJavaTypeValue.getValue().getName();
		return findOrBindEntityBinding( referencedEntityName );
	}

	private EntityBinding findOrBindEntityBinding(final String entityName) {
		// Check if binding has already been created
		EntityBinding entityBinding = metadata.getEntityBinding( entityName );
		if ( entityBinding == null ) {
			 throw bindingContext().makeMappingException(
					 String.format( "No entity binding with name: %s", entityName )
			 );
			// Find appropriate source to create binding
			/*
			final EntitySource entitySource = entitySourcesByName.get( entityName );
			if ( entitySource == null ) {
				String msg = log.missingEntitySource( entityName );
				throw bindingContext().makeMappingException( msg );
			}

			// Get super entity binding (creating it if necessary using recursive call to this method)
			if ( SubclassEntitySource.class.isInstance( entitySource ) ) {
				String superEntityName = ( (SubclassEntitySource) entitySource ).superclassEntitySource()
						.getEntityName();
				EntityBinding superEntityBinding = findOrBindEntityBinding( superEntityName );
				entityBinding = bindSubEntity( superEntityBinding, entitySource );
			}
			else {
				EntityHierarchy entityHierarchy = entityHierarchiesByRootEntitySource.get(
						RootEntitySource.class.cast(
								entitySource
						)
				);
				entityBinding = bindEntityHierarchy( entityHierarchy );
			}
			*/
		}
		return entityBinding;
	}

	private EntityBinding createEntityBinding(
			final EntityBinding superEntityBinding,
			final EntitySource entitySource) {
		// Create binding
		final InheritanceType inheritanceType = inheritanceTypes.peek();
		final EntityMode entityMode = entityModes.peek();
		final EntityBinding entityBinding =
				entitySource instanceof RootEntitySource ? new EntityBinding(
						inheritanceType,
						entityMode
				) : new EntityBinding(
						superEntityBinding
				);
		// Create domain entity
		final String entityClassName = entityMode == EntityMode.POJO ? entitySource.getClassName() : null;
		LocalBindingContext bindingContext = bindingContext();
		entityBinding.setEntity(
				new Entity(
						entitySource.getEntityName(),
						entityClassName,
						bindingContext.makeClassReference( entityClassName ),
						superEntityBinding == null ? null : superEntityBinding.getEntity()
				)
		);

		entityBinding.setEntityName( entitySource.getEntityName() );
		entityBinding.setJpaEntityName( entitySource.getJpaEntityName() );          //must before creating primary table
		entityBinding.setDynamicUpdate( entitySource.isDynamicUpdate() );
		entityBinding.setDynamicInsert( entitySource.isDynamicInsert() );
		entityBinding.setBatchSize( entitySource.getBatchSize() );
		entityBinding.setSelectBeforeUpdate( entitySource.isSelectBeforeUpdate() );
		entityBinding.setAbstract( entitySource.isAbstract() );
		entityBinding.setCustomLoaderName( entitySource.getCustomLoaderName() );
		entityBinding.setCustomInsert( entitySource.getCustomSqlInsert() );
		entityBinding.setCustomUpdate( entitySource.getCustomSqlUpdate() );
		entityBinding.setCustomDelete( entitySource.getCustomSqlDelete() );
		entityBinding.setJpaCallbackClasses( entitySource.getJpaCallbackClasses() );

		// todo: deal with joined and unioned subclass bindings
		// todo: bind fetch profiles
		// Configure rest of binding
		final String customTuplizerClassName = entitySource.getCustomTuplizerClassName();
		if ( customTuplizerClassName != null ) {
			entityBinding.setCustomEntityTuplizerClass(
					bindingContext.<EntityTuplizer>locateClassByName(
							customTuplizerClassName
					)
			);
		}
		final String customPersisterClassName = entitySource.getCustomPersisterClassName();
		if ( customPersisterClassName != null ) {
			entityBinding.setCustomEntityPersisterClass(
					bindingContext.<EntityPersister>locateClassByName(
							customPersisterClassName
					)
			);
		}
		entityBinding.setMetaAttributeContext(
				createMetaAttributeContext(
						entitySource.getMetaAttributeSources(),
						true,
						metadata.getGlobalMetaAttributeContext()
				)
		);

		if ( entitySource.getSynchronizedTableNames() != null ) {
			entityBinding.addSynchronizedTableNames( entitySource.getSynchronizedTableNames() );
		}
		resolveEntityLaziness( entityBinding, entitySource );
		if ( entitySource.getFilterSources() != null ) {
			for ( FilterSource filterSource : entitySource.getFilterSources() ) {
				entityBinding.addFilterConfiguration( createFilterConfiguration( filterSource, entityBinding ) );
			}
		}
		// Register binding with metadata
		metadata.addEntity( entityBinding );
		return entityBinding;
	}

	private FilterConfiguration createFilterConfiguration(FilterSource filterSource, EntityBinding entityBinding){
		String condition = filterSource.getCondition();
		if(StringHelper.isEmpty( condition )){
			FilterDefinition filterDefinition = metadata.getFilterDefinitions().get( filterSource.getName() );
			if(filterDefinition == null){
				throw bindingContext().makeMappingException( String.format( "Filter[%s] doesn't have a condition", filterSource.getName() ) );
			}
			condition = filterDefinition.getDefaultFilterCondition();
		}
		return new FilterConfiguration(
				filterSource.getName(),
				condition,
				filterSource.shouldAutoInjectAliases(),
				filterSource.getAliasToTableMap(),
				filterSource.getAliasToEntityMap(),
				entityBinding
		);
	}

	private void resolveEntityLaziness(
			final EntityBinding entityBinding,
			final EntitySource entitySource) {
		if ( entityModes.peek() == EntityMode.POJO ) {
			final String proxy = entitySource.getProxy();
			if ( proxy == null ) {
				if ( entitySource.isLazy() ) {
					entityBinding.setProxyInterfaceType( entityBinding.getEntity().getClassReferenceUnresolved() );
					entityBinding.setLazy( true );
				}
			}
			else {
				entityBinding.setProxyInterfaceType(
						bindingContext().makeClassReference(
								bindingContext().qualifyClassName(
										proxy
								)
						)
				);
				entityBinding.setLazy( true );
			}
		}
		else {
			entityBinding.setProxyInterfaceType( null );
			entityBinding.setLazy( entitySource.isLazy() );
		}
	}

	private void bindSubEntities(
			final EntityBinding entityBinding,
			final EntitySource entitySource) {
		for ( final SubclassEntitySource subEntitySource : entitySource.subclassEntitySources() ) {
			bindSubEntity( entityBinding, subEntitySource );
		}
	}

	private EntityBinding bindSubEntity(
			final EntityBinding superEntityBinding,
			final EntitySource entitySource) {
		// Return existing binding if available
		EntityBinding entityBinding = metadata.getEntityBinding( entitySource.getEntityName() );
		if ( entityBinding != null ) {
			return entityBinding;
		}
		final LocalBindingContext bindingContext = entitySource.getLocalBindingContext();
		bindingContexts.push( bindingContext );
		try {
			// Create new entity binding
			entityBinding = createEntityBinding( superEntityBinding, entitySource );
			entityBinding.setMutable( entityBinding.getHierarchyDetails().getRootEntityBinding().isMutable() );
			markSuperEntityTableAbstractIfNecessary( superEntityBinding );
			bindPrimaryTable( entityBinding, entitySource );
			bindSubEntityPrimaryKey( entityBinding, entitySource );
			bindSecondaryTables( entityBinding, entitySource );
			bindUniqueConstraints( entityBinding, entitySource );
			bindAttributes( entityBinding, entitySource );
			bindSubEntities( entityBinding, entitySource );
			return entityBinding;
		}
		finally {
			bindingContexts.pop();
		}
	}

	private void bindDiscriminator(
			final EntityBinding rootEntityBinding,
			final RootEntitySource rootEntitySource) {
		final DiscriminatorSource discriminatorSource = rootEntitySource.getDiscriminatorSource();
		if ( discriminatorSource == null ) {
			return;
		}
		final RelationalValueSource valueSource = discriminatorSource.getDiscriminatorRelationalValueSource();
		final TableSpecification table = rootEntityBinding.locateTable( valueSource.getContainingTableName() );
		final Value value = buildDiscriminatorRelationValue( valueSource, table );
		final EntityDiscriminator discriminator =
				new EntityDiscriminator( value, discriminatorSource.isInserted(), discriminatorSource.isForced() );
		rootEntityBinding.getHierarchyDetails().setEntityDiscriminator( discriminator );
		final String discriminatorValue = rootEntitySource.getDiscriminatorMatchValue();
		if ( discriminatorValue != null ) {
			rootEntityBinding.setDiscriminatorMatchValue( discriminatorValue );
		}
		else if ( !Modifier.isAbstract(
				bindingContext().locateClassByName( rootEntitySource.getEntityName() )
						.getModifiers()
		) ) {
			// Use the class name as a default if no discriminator value.
			// However, skip abstract classes -- obviously no discriminators there.
			rootEntityBinding.setDiscriminatorMatchValue( rootEntitySource.getEntityName() );
		}
		// Configure discriminator hibernate type
		typeHelper.bindDiscriminatorType( discriminator, value );
	}



	private void bindVersion(
			final EntityBinding rootEntityBinding,
			final VersionAttributeSource versionAttributeSource) {
		if ( versionAttributeSource == null ) {
			return;
		}
		final EntityVersion version = rootEntityBinding.getHierarchyDetails().getEntityVersion();
		version.setVersioningAttributeBinding(
				(BasicAttributeBinding) bindAttribute(
						rootEntityBinding,
						versionAttributeSource
				)
		);
		// ensure version is non-nullable
		for ( RelationalValueBinding valueBinding : version.getVersioningAttributeBinding()
				.getRelationalValueBindings() ) {
			if ( !valueBinding.isDerived() ) {
				( (Column) valueBinding.getValue() ).setNullable( false );
			}
		}
		version.setUnsavedValue(
				versionAttributeSource.getUnsavedValue() == null
						? "undefined"
						: versionAttributeSource.getUnsavedValue()
		);
	}

	private void bindMultiTenancy(
			final EntityBinding rootEntityBinding,
			final RootEntitySource rootEntitySource) {
		final MultiTenancySource multiTenancySource = rootEntitySource.getMultiTenancySource();
		if ( multiTenancySource == null ) {
			return;
		}

		// if (1) the strategy is discriminator based and (2) the entity is not shared, we need to either (a) extract
		// the user supplied tenant discriminator value mapping or (b) generate an implicit one
		final boolean needsTenantIdentifierValueMapping =
				MultiTenancyStrategy.DISCRIMINATOR == metadata.getOptions().getMultiTenancyStrategy()
						&& !multiTenancySource.isShared();

		if ( needsTenantIdentifierValueMapping ) {
			// NOTE : the table for tenant identifier/discriminator is always the primary table
			final Value tenantDiscriminatorValue;
			final RelationalValueSource valueSource = multiTenancySource.getRelationalValueSource();
			if ( valueSource == null ) {
				// user supplied no explicit information, so use implicit mapping with default name
				tenantDiscriminatorValue = rootEntityBinding.getPrimaryTable().locateOrCreateColumn( "tenant_id" );
			}
			else {
				tenantDiscriminatorValue = buildDiscriminatorRelationValue(
						valueSource,
						rootEntityBinding.getPrimaryTable()
				);
			}
			rootEntityBinding.getHierarchyDetails()
					.getTenantDiscrimination()
					.setDiscriminatorValue( tenantDiscriminatorValue );
		}

		rootEntityBinding.getHierarchyDetails().getTenantDiscrimination().setShared( multiTenancySource.isShared() );
		rootEntityBinding.getHierarchyDetails()
				.getTenantDiscrimination()
				.setUseParameterBinding( multiTenancySource.bindAsParameter() );
	}

	private void bindPrimaryTable(
			final EntityBinding entityBinding,
			final EntitySource entitySource) {
		final EntityBinding superEntityBinding = entityBinding.getSuperEntityBinding();
		final InheritanceType inheritanceType = entityBinding.getHierarchyDetails().getInheritanceType();
		final TableSpecification table;
		final String tableName;
		// single table and sub entity
		if ( superEntityBinding != null && inheritanceType == InheritanceType.SINGLE_TABLE ) {
			table = superEntityBinding.getPrimaryTable();
			tableName = superEntityBinding.getPrimaryTableName();
			// Configure discriminator if present
			final String discriminatorValue = entitySource.getDiscriminatorMatchValue() != null ?
					entitySource.getDiscriminatorMatchValue()
					: entitySource.getEntityName();
			entityBinding.setDiscriminatorMatchValue( discriminatorValue );
		}

		// single table and root entity
		// joined
		// table per class and non-abstract  entity
		else {
			Table includedTable = null;
			if ( superEntityBinding != null
					&& inheritanceType == InheritanceType.TABLE_PER_CLASS
					&& Table.class.isInstance( superEntityBinding.getPrimaryTable() ) ) {
				includedTable = Table.class.cast( superEntityBinding.getPrimaryTable() );
			}
			table = createTable(
					entitySource.getPrimaryTable(), new TableNamingStrategyHelper( entityBinding ),includedTable
			);
			tableName = table.getLogicalName().getText();
		}
		entityBinding.setPrimaryTable( table );
		entityBinding.setPrimaryTableName( tableName );
	}

	private void bindSecondaryTables(
			final EntityBinding entityBinding,
			final EntitySource entitySource) {
		for ( final SecondaryTableSource secondaryTableSource : entitySource.getSecondaryTables() ) {
			final TableSpecification table = createTable( secondaryTableSource.getTableSource(), new TableNamingStrategyHelper( entityBinding ) );
			table.addComment( secondaryTableSource.getComment() );
			final List<RelationalValueBinding> joinRelationalValueBindings;
			// TODO: deal with property-refs???
			if ( secondaryTableSource.getPrimaryKeyColumnSources().isEmpty() ) {
				final List<Column> joinedColumns = entityBinding.getPrimaryTable().getPrimaryKey().getColumns();
				joinRelationalValueBindings = new ArrayList<RelationalValueBinding>( joinedColumns.size() );
				for ( Column joinedColumn : joinedColumns ) {
					Column joinColumn = table.locateOrCreateColumn(
							bindingContext().getNamingStrategy().joinKeyColumnName(
									joinedColumn.getColumnName().getText(),
									entityBinding.getPrimaryTable().getLogicalName().getText()
							)
					);
					joinRelationalValueBindings.add( new RelationalValueBinding( entityBinding.getPrimaryTable(), joinColumn, true, false ) );
				}
			}
			else {
				joinRelationalValueBindings = new ArrayList<RelationalValueBinding>(
						secondaryTableSource.getPrimaryKeyColumnSources()
								.size()
				);
				final List<Column> primaryKeyColumns = entityBinding.getPrimaryTable().getPrimaryKey().getColumns();
				if ( primaryKeyColumns.size() != secondaryTableSource.getPrimaryKeyColumnSources().size() ) {
					throw bindingContext().makeMappingException(
							String.format(
									"The number of primary key column sources provided for a secondary table is not equal to the number of columns in the primary key for [%s].",
									entityBinding.getEntityName()
							)
					);
				}
				for ( int i = 0; i < primaryKeyColumns.size(); i++ ) {
					// todo : apply naming strategy to infer missing column name
					final ColumnSource joinColumnSource = secondaryTableSource.getPrimaryKeyColumnSources().get( i );
					Column column = table.locateColumn( joinColumnSource.getName() );
					if ( column == null ) {
						column = table.createColumn( joinColumnSource.getName() );
						if ( joinColumnSource.getSqlType() != null ) {
							column.setSqlType( joinColumnSource.getSqlType() );
						}
					}
					joinRelationalValueBindings.add( new RelationalValueBinding( table, column, true, false ) );
				}
			}
			typeHelper.bindJdbcDataType(
					entityBinding.getHierarchyDetails()
							.getEntityIdentifier()
							.getAttributeBinding()
							.getHibernateTypeDescriptor()
							.getResolvedTypeMapping(),
					joinRelationalValueBindings
			);

			// TODO: make the foreign key column the primary key???
			final List<Column> targetColumns = determineForeignKeyTargetColumns( entityBinding, secondaryTableSource );
			final ForeignKey foreignKey = locateOrCreateForeignKey(
					quotedIdentifier( secondaryTableSource.getExplicitForeignKeyName() ),
					table,
					joinRelationalValueBindings,
					determineForeignKeyTargetTable( entityBinding, secondaryTableSource ),
					targetColumns
			);
			SecondaryTable secondaryTable = new SecondaryTable( table, foreignKey );
			secondaryTable.setFetchStyle( secondaryTableSource.getFetchStyle() );
			secondaryTable.setInverse( secondaryTableSource.isInverse() );
			secondaryTable.setOptional( secondaryTableSource.isOptional() );
			secondaryTable.setCascadeDeleteEnabled( secondaryTableSource.isCascadeDeleteEnabled() );
			entityBinding.addSecondaryTable( secondaryTable );
		}
	}

	public ForeignKey locateOrCreateForeignKey(
			final String foreignKeyName,
			final TableSpecification sourceTable,
			final List<RelationalValueBinding> sourceRelationalValueBindings,
			final TableSpecification targetTable,
			final List<Column> targetColumns) {
		return foreignKeyHelper.locateOrCreateForeignKey(
				foreignKeyName,
				sourceTable,
				extractColumnsFromRelationalValueBindings( sourceRelationalValueBindings ),
				targetTable,
				targetColumns
		);
	}

	// TODO: try to get rid of this...
	private static List<Column> extractColumnsFromRelationalValueBindings(
			final List<RelationalValueBinding> valueBindings) {
		List<Column> columns = new ArrayList<Column>( valueBindings.size() );
		for ( RelationalValueBinding relationalValueBinding : valueBindings ) {
			final Value value = relationalValueBinding.getValue();
			// todo : currently formulas are not supported here... :(
			if ( !Column.class.isInstance( value ) ) {
				throw new NotYetImplementedException(
						"Derived values are not supported when creating a foreign key that targets columns."
				);
			}
			columns.add( (Column) value );
		}
		return columns;
	}

	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ identifier binding relates methods
	private void bindIdentifier(
			final EntityBinding rootEntityBinding,
			final IdentifierSource identifierSource) {
		final EntityIdentifierNature nature = identifierSource.getNature();
		switch ( nature ) {
			case SIMPLE: {
				bindSimpleIdentifier( rootEntityBinding, (SimpleIdentifierSource) identifierSource );
				break;
			}
			case AGGREGATED_COMPOSITE: {
				bindAggregatedCompositeIdentifier(
						rootEntityBinding,
						(AggregatedCompositeIdentifierSource) identifierSource
				);
				break;
			}
			case NON_AGGREGATED_COMPOSITE: {
				bindNonAggregatedCompositeIdentifier(
						rootEntityBinding,
						(NonAggregatedCompositeIdentifierSource) identifierSource
				);
				break;
			}
			default: {
				throw bindingContext().makeMappingException( "Unknown identifier nature : " + nature.name() );
			}
		}
	}

	private void bindSimpleIdentifier(
			final EntityBinding rootEntityBinding,
			final SimpleIdentifierSource identifierSource) {
		// locate the attribute binding
		final BasicAttributeBinding idAttributeBinding = (BasicAttributeBinding) bindIdentifierAttribute(
				rootEntityBinding, identifierSource.getIdentifierAttributeSource()
		);

		// Configure ID generator
		IdGenerator generator = identifierSource.getIdentifierGeneratorDescriptor();
		if ( generator == null ) {
			final Map<String, String> params = new HashMap<String, String>();
			params.put( IdentifierGenerator.ENTITY_NAME, rootEntityBinding.getEntity().getName() );
			generator = new IdGenerator( "default_assign_identity_generator", "assigned", params );
		}

		// determine the unsaved value mapping
		final String unsavedValue = interpretIdentifierUnsavedValue( identifierSource, generator );

		rootEntityBinding.getHierarchyDetails().getEntityIdentifier().prepareAsSimpleIdentifier(
				idAttributeBinding,
				generator,
				unsavedValue
		);
	}

	private void bindAggregatedCompositeIdentifier(
			final EntityBinding rootEntityBinding,
			final AggregatedCompositeIdentifierSource identifierSource) {
		// locate the attribute binding
		final CompositeAttributeBinding idAttributeBinding =
				(CompositeAttributeBinding) bindIdentifierAttribute(
						rootEntityBinding, identifierSource.getIdentifierAttributeSource()
				);

		// Configure ID generator
		IdGenerator generator = identifierSource.getIdentifierGeneratorDescriptor();
		if ( generator == null ) {
			final Map<String, String> params = new HashMap<String, String>();
			params.put( IdentifierGenerator.ENTITY_NAME, rootEntityBinding.getEntity().getName() );
			generator = new IdGenerator( "default_assign_identity_generator", "assigned", params );
		}

		// determine the unsaved value mapping
		final String unsavedValue = interpretIdentifierUnsavedValue( identifierSource, generator );

		rootEntityBinding.getHierarchyDetails().getEntityIdentifier().prepareAsAggregatedCompositeIdentifier(
				idAttributeBinding,
				generator,
				unsavedValue
		);
	}

	private void bindNonAggregatedCompositeIdentifier(
			final EntityBinding rootEntityBinding,
			final NonAggregatedCompositeIdentifierSource identifierSource) {
		// locate the attribute bindings for the real attributes
		List<SingularAttributeBinding> idAttributeBindings =
				new ArrayList<SingularAttributeBinding>();
		for ( SingularAttributeSource attributeSource : identifierSource.getAttributeSourcesMakingUpIdentifier() ) {
			SingularAttributeBinding singularAttributeBinding =
					bindIdentifierAttribute( rootEntityBinding, attributeSource );
			idAttributeBindings.add( singularAttributeBinding );
		}

		final Class<?> idClassClass = identifierSource.getLookupIdClass();
		final String idClassPropertyAccessorName =
				idClassClass == null ?
						null :
						propertyAccessorName( identifierSource.getIdClassPropertyAccessorName() );

		// Configure ID generator
		IdGenerator generator = identifierSource.getIdentifierGeneratorDescriptor();
		if ( generator == null ) {
			final Map<String, String> params = new HashMap<String, String>();
			params.put( IdentifierGenerator.ENTITY_NAME, rootEntityBinding.getEntity().getName() );
			generator = new IdGenerator( "default_assign_identity_generator", "assigned", params );
		}
		// Create the synthetic attribute
		final SingularAttribute syntheticAttribute =
				rootEntityBinding.getEntity().createSyntheticCompositeAttribute(
						SYNTHETIC_COMPOSITE_ID_ATTRIBUTE_NAME,
						rootEntityBinding.getEntity()
				);

		final CompositeAttributeBinding syntheticAttributeBinding =
				rootEntityBinding.makeVirtualCompositeAttributeBinding(
						syntheticAttribute,
						createMetaAttributeContext( rootEntityBinding, identifierSource.getMetaAttributeSources() ),
						idAttributeBindings
				);
		// Create the synthetic attribute binding.
		rootEntityBinding.getHierarchyDetails().getEntityIdentifier().prepareAsNonAggregatedCompositeIdentifier(
				syntheticAttributeBinding,
				generator,
				interpretIdentifierUnsavedValue( identifierSource, generator ),
				idClassClass,
				idClassPropertyAccessorName
		);

		typeHelper.bindNonAggregatedCompositeIdentifierType( syntheticAttributeBinding, syntheticAttribute );
	}

	private void bindIdentifierGenerator(final EntityBinding rootEntityBinding) {
		final Properties properties = new Properties();
		properties.putAll( metadata.getServiceRegistry().getService( ConfigurationService.class ).getSettings() );
		if ( !properties.contains( AvailableSettings.PREFER_POOLED_VALUES_LO ) ) {
			properties.put( AvailableSettings.PREFER_POOLED_VALUES_LO, "false" );
		}
		if ( !properties.contains( PersistentIdentifierGenerator.IDENTIFIER_NORMALIZER ) ) {
			properties.put( PersistentIdentifierGenerator.IDENTIFIER_NORMALIZER, nameNormalizer );
		}
		final EntityIdentifier entityIdentifier = rootEntityBinding.getHierarchyDetails().getEntityIdentifier();
		entityIdentifier.createIdentifierGenerator( identifierGeneratorFactory, properties );
		if ( IdentityGenerator.class.isInstance( entityIdentifier.getIdentifierGenerator() ) ) {
			if ( rootEntityBinding.getPrimaryTable().getPrimaryKey().getColumnSpan() != 1 ) {
				throw bindingContext().makeMappingException(
						String.format(
								"ID for %s is mapped as an identity with %d columns. IDs mapped as an identity can only have 1 column.",
								rootEntityBinding.getEntity().getName(),
								rootEntityBinding.getPrimaryTable().getPrimaryKey().getColumnSpan()
						)
				);
			}
			rootEntityBinding.getPrimaryTable().getPrimaryKey().getColumns().get( 0 ).setIdentity( true );
		}
		if ( PersistentIdentifierGenerator.class.isInstance( entityIdentifier.getIdentifierGenerator() ) ) {
			( (PersistentIdentifierGenerator) entityIdentifier.getIdentifierGenerator() ).registerExportables( metadata.getDatabase() );
		}
	}

	private SingularAttributeBinding bindIdentifierAttribute(
			final AttributeBindingContainer attributeBindingContainer,
			final SingularAttributeSource attributeSource) {
		return bindSingularAttribute( attributeBindingContainer, attributeSource, true );
	}

	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ Attributes binding relates methods
	private void bindAttributes(
			final AttributeBindingContainer attributeBindingContainer,
			final AttributeSourceContainer attributeSourceContainer) {
		for ( final AttributeSource attributeSource : attributeSourceContainer.attributeSources() ) {
			bindAttribute( attributeBindingContainer, attributeSource );
		}
	}

	private void bindAttributes(
			final CompositeAttributeBindingContainer compositeAttributeBindingContainer,
			final AttributeSourceContainer attributeSourceContainer) {
		if ( compositeAttributeBindingContainer.getParentReference() == null ) {
			bindAttributes(
					(AttributeBindingContainer) compositeAttributeBindingContainer,
					attributeSourceContainer
			);
		}
		else {
			for ( final AttributeSource subAttributeSource : attributeSourceContainer.attributeSources() ) {
				if ( !subAttributeSource.getName()
						.equals( compositeAttributeBindingContainer.getParentReference().getName() ) ) {
					bindAttribute(
							compositeAttributeBindingContainer,
							subAttributeSource
					);
				}
			}
		}

	}

	private AttributeBinding bindAttribute(
			final AttributeBindingContainer attributeBindingContainer,
			final AttributeSource attributeSource) {
		// Return existing binding if available
		final String attributeName = attributeSource.getName();
		final AttributeBinding attributeBinding = attributeBindingContainer.locateAttributeBinding( attributeName );
		if ( attributeBinding != null ) {
			return attributeBinding;
		}
		return attributeSource.isSingular() ?
				bindSingularAttribute(
						attributeBindingContainer,
						SingularAttributeSource.class.cast( attributeSource ),
						false
				) :
				bindPluralAttribute( attributeBindingContainer, PluralAttributeSource.class.cast( attributeSource ) );
	}

	private BasicAttributeBinding bindBasicAttribute(
			final AttributeBindingContainer attributeBindingContainer,
			final SingularAttributeSource attributeSource,
			SingularAttribute attribute) {
		if ( attribute == null ) {
			attribute = createSingularAttribute( attributeBindingContainer, attributeSource );
		}
		final List<RelationalValueBinding> relationalValueBindings =
				bindValues(
						attributeBindingContainer,
						attributeSource,
						attribute,
						locateDefaultTableSpecificationForAttribute( attributeBindingContainer, attributeSource ),
						false
				);
		final BasicAttributeBinding attributeBinding =
				attributeBindingContainer.makeBasicAttributeBinding(
						attribute,
						relationalValueBindings,
						propertyAccessorName( attributeSource ),
						attributeSource.isIncludedInOptimisticLocking(),
						attributeSource.isLazy(),
						attributeSource.getNaturalIdMutability(),
						createMetaAttributeContext( attributeBindingContainer, attributeSource ),
						attributeSource.getGeneration()
				);
		typeHelper.bindSingularAttributeType(
				attributeSource,
				attributeBinding
		);
		return attributeBinding;
	}

	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ singular attributes binding
	private SingularAttributeBinding bindSingularAttribute(
			final AttributeBindingContainer attributeBindingContainer,
			final SingularAttributeSource attributeSource,
			final boolean isIdentifierAttribute) {
		final SingularAttributeSource.Nature nature = attributeSource.getNature();
		final SingularAttribute attribute =
				attributeBindingContainer.getAttributeContainer().locateSingularAttribute( attributeSource.getName() );
		switch ( nature ) {
			case BASIC:
				return bindBasicAttribute( attributeBindingContainer, attributeSource, attribute );
			case ONE_TO_ONE:
				return bindOneToOneAttribute(
						attributeBindingContainer,
						ToOneAttributeSource.class.cast( attributeSource ),
						attribute
				);
			case MANY_TO_ONE:
				return bindManyToOneAttribute(
						attributeBindingContainer,
						ToOneAttributeSource.class.cast( attributeSource ),
						attribute
				);
			case COMPOSITE:
				return bindAggregatedCompositeAttribute(
						attributeBindingContainer,
						ComponentAttributeSource.class.cast( attributeSource ),
						attribute,
						isIdentifierAttribute
				);
			default:
				throw new NotYetImplementedException( nature.toString() );
		}
	}

	private CompositeAttributeBinding bindAggregatedCompositeAttribute(
			final AttributeBindingContainer attributeBindingContainer,
			final ComponentAttributeSource attributeSource,
			SingularAttribute attribute,
			boolean isAttributeIdentifier) {
		CompositeAttributeBinding attributeBinding = createAggregatedCompositeAttribute(
				attributeBindingContainer,
				attributeSource,
				attribute
		);
		bindAttributes( attributeBinding, attributeSource );
		typeHelper.bindAggregatedCompositeAttributeType(
				isAttributeIdentifier,
				(Aggregate) attributeBinding.getAttribute().getSingularAttributeType(),
				null, // TODO: don't have the default value at this point; shouldn't be needed...
				attributeBinding
		);
		return attributeBinding;
	}

	private CompositeAttributeBinding createAggregatedCompositeAttribute(
			final AttributeBindingContainer attributeBindingContainer,
			final ComponentAttributeSource attributeSource,
			SingularAttribute attribute) {
		final Aggregate composite;
		ValueHolder<Class<?>> defaultJavaClassReference = null;
		if ( attribute == null ) {
			if ( attributeSource.getClassName() != null ) {
				composite = new Aggregate(
						attributeSource.getPath(),
						attributeSource.getClassName(),
						attributeSource.getClassReference() != null ?
								attributeSource.getClassReference() :
								bindingContext().makeClassReference( attributeSource.getClassName() ),
						null
				);
				// no need for a default because there's an explicit class name provided
			}
			else {
				defaultJavaClassReference = createSingularAttributeJavaType(
						attributeBindingContainer.getClassReference(), attributeSource.getName()
				);
				composite = new Aggregate(
						attributeSource.getPath(),
						defaultJavaClassReference.getValue().getName(),
						defaultJavaClassReference,
						null
				);
			}
			attribute = attributeBindingContainer.getAttributeContainer().createCompositeAttribute(
					attributeSource.getName(),
					composite
			);
		}
		else {
			composite = (Aggregate) attribute.getSingularAttributeType();
		}

		final SingularAttribute referencingAttribute =
				StringHelper.isEmpty( attributeSource.getParentReferenceAttributeName() ) ?
						null :
						composite.createSingularAttribute( attributeSource.getParentReferenceAttributeName() );
		final NaturalIdMutability naturalIdMutability = attributeSource.getNaturalIdMutability();
		final CompositeAttributeBinding attributeBinding =
				attributeBindingContainer.makeAggregatedCompositeAttributeBinding(
						attribute,
						referencingAttribute,
						propertyAccessorName( attributeSource ),
						attributeSource.isIncludedInOptimisticLocking(),
						attributeSource.isLazy(),
						naturalIdMutability,
						createMetaAttributeContext( attributeBindingContainer, attributeSource )
				);
		if ( attributeSource.getExplicitTuplizerClassName() != null ) {
			Class tuplizerClass = bindingContext().getServiceRegistry()
					.getService( ClassLoaderService.class )
					.classForName( attributeSource.getExplicitTuplizerClassName() );
			attributeBinding.setCustomComponentTuplizerClass( tuplizerClass );
		}
		return attributeBinding;
	}

	/**
	 * todo: if the not found exception is ignored, here we should create an unique key instead of FK
	 * this guard method should be removed after we implement this.
	 */
	private void throwExceptionIfNotFoundIgnored(boolean isNotFoundAnException) {
		if ( !isNotFoundAnException ) {
			throw new NotYetImplementedException( "association of ignored not found exception is not yet implemented" );
		}
	}

	private ManyToOneAttributeBinding bindManyToOneAttribute(
			final AttributeBindingContainer attributeBindingContainer,
			final ToOneAttributeSource attributeSource,
			SingularAttribute attribute) {
		final SingularAttribute actualAttribute =
				attribute != null ?
						attribute :
						createSingularAttribute( attributeBindingContainer, attributeSource );
		throwExceptionIfNotFoundIgnored( attributeSource.isNotFoundAnException() );

		ToOneAttributeBindingContext toOneAttributeBindingContext = new ToOneAttributeBindingContext() {
			@Override
			public SingularAssociationAttributeBinding createToOneAttributeBinding(
					EntityBinding referencedEntityBinding,
					SingularAttributeBinding referencedAttributeBinding
			) {
				/**
				 * this is not correct, here, if no @JoinColumn defined, we simply create the FK column only with column calucated
				 * but what we should do is get all the column info from the referenced column(s), including nullable, size etc.
				 */
				final TableSpecification table = locateDefaultTableSpecificationForAttribute(
						attributeBindingContainer,
						attributeSource
				);
				final List<RelationalValueBinding> relationalValueBindings =
						bindValues(
								attributeBindingContainer,
								attributeSource,
								actualAttribute,
								table,
								attributeSource.getDefaultNamingStrategies(
										attributeBindingContainer.seekEntityBinding().getEntity().getName(),
										table.getLogicalName().getText(),
										referencedAttributeBinding
								),
								false
						);

				// todo : currently a chicken-egg problem here between creating the attribute binding and binding its FK values...
				// now we have everything to create a ManyToOneAttributeBinding
				return attributeBindingContainer.makeManyToOneAttributeBinding(
						actualAttribute,
						propertyAccessorName( attributeSource ),
						attributeSource.isIncludedInOptimisticLocking(),
						attributeSource.isLazy(),
						attributeSource.isNotFoundAnException(),
						attributeSource.getNaturalIdMutability(),
						createMetaAttributeContext( attributeBindingContainer, attributeSource ),
						referencedEntityBinding,
						referencedAttributeBinding,
						relationalValueBindings
				);
			}

			@Override
			public EntityType resolveEntityType(
					EntityBinding referencedEntityBinding,
					SingularAttributeBinding referencedAttributeBinding) {
				final SingularNonAssociationAttributeBinding idAttributeBinding =
						referencedEntityBinding.getHierarchyDetails().getEntityIdentifier().getAttributeBinding();
				final String uniqueKeyAttributeName =
						idAttributeBinding == referencedAttributeBinding ?
								null :
								getRelativePathFromEntityName( referencedAttributeBinding );
//				final boolean isNotFoundAnException = SingularAssociationAttributeBinding.class.isInstance(  )

				return metadata.getTypeResolver().getTypeFactory().manyToOne(
						referencedEntityBinding.getEntity().getName(),
						uniqueKeyAttributeName,
						attributeSource.getFetchTiming() != FetchTiming.IMMEDIATE,
						attributeSource.isUnWrapProxy(),
						!attributeSource.isNotFoundAnException(),
						attributeSource.isUnique()
				);
			}
		};

		final ManyToOneAttributeBinding attributeBinding = (ManyToOneAttributeBinding) bindToOneAttribute(
				actualAttribute,
				attributeSource,
				toOneAttributeBindingContext
		);
		typeHelper.bindJdbcDataType(
				attributeBinding.getHibernateTypeDescriptor().getResolvedTypeMapping(),
				attributeBinding.getRelationalValueBindings()
		);
		final List<Column> targetColumns = determineForeignKeyTargetColumns(
				attributeBinding.getReferencedEntityBinding(),
				attributeSource
		);
		if ( !attributeBinding.hasDerivedValue() ) {
			locateOrCreateForeignKey(
					quotedIdentifier( attributeSource.getExplicitForeignKeyName() ),
					attributeBinding.getRelationalValueBindings().get( 0 ).getTable(),
					attributeBinding.getRelationalValueBindings(),
					determineForeignKeyTargetTable( attributeBinding.getReferencedEntityBinding(), attributeSource ),
					targetColumns
			);
		}
		return attributeBinding;
	}

	private OneToOneAttributeBinding bindOneToOneAttribute(
			final AttributeBindingContainer attributeBindingContainer,
			final ToOneAttributeSource attributeSource,
			SingularAttribute attribute) {
		final SingularAttribute actualAttribute =
				attribute != null ?
						attribute :
						createSingularAttribute( attributeBindingContainer, attributeSource );

		ToOneAttributeBindingContext toOneAttributeBindingContext = new ToOneAttributeBindingContext() {
			@Override
			public SingularAssociationAttributeBinding createToOneAttributeBinding(
					EntityBinding referencedEntityBinding,
					SingularAttributeBinding referencedAttributeBinding
			) {
				/**
				 * this is not correct, here, if no @JoinColumn defined, we simply create the FK column only with column calucated
				 * but what we should do is get all the column info from the referenced column(s), including nullable, size etc.
				 */
				final TableSpecification table = locateDefaultTableSpecificationForAttribute(
						attributeBindingContainer,
						attributeSource
				);
				final List<RelationalValueBinding> relationalValueBindings;
				if ( ! attributeSource.relationalValueSources().isEmpty() ) {
					relationalValueBindings =
							bindValues(
									attributeBindingContainer,
									attributeSource,
									actualAttribute,
									table,
									attributeSource.getDefaultNamingStrategies(
											attributeBindingContainer.seekEntityBinding().getEntity().getName(),
											table.getLogicalName().getText(),
											referencedAttributeBinding
									),
									false
							);
				}
				else {
					relationalValueBindings = Collections.emptyList();
				}

				// todo : currently a chicken-egg problem here between creating the attribute binding and binding its FK values...
				return attributeBindingContainer.makeOneToOneAttributeBinding(
						actualAttribute,
						propertyAccessorName( attributeSource ),
						attributeSource.isIncludedInOptimisticLocking(),
						attributeSource.isLazy(),
						attributeSource.getNaturalIdMutability(),
						createMetaAttributeContext( attributeBindingContainer, attributeSource ),
						referencedEntityBinding,
						referencedAttributeBinding,
						attributeSource.getForeignKeyDirection() == ForeignKeyDirection.FROM_PARENT,
						relationalValueBindings
				);
			}

			@Override
			public EntityType resolveEntityType(
					EntityBinding referencedEntityBinding,
					SingularAttributeBinding referencedAttributeBinding) {
				final SingularNonAssociationAttributeBinding idAttributeBinding =
						referencedEntityBinding.getHierarchyDetails().getEntityIdentifier().getAttributeBinding();
				final String uniqueKeyAttributeName =
						idAttributeBinding == referencedAttributeBinding ?
								null :
								getRelativePathFromEntityName( referencedAttributeBinding );
				if ( attributeSource.relationalValueSources().isEmpty() )  {
					return metadata.getTypeResolver().getTypeFactory().oneToOne(
							referencedEntityBinding.getEntity().getName(),
							attributeSource.getForeignKeyDirection(),
							uniqueKeyAttributeName,
							attributeSource.getFetchTiming() != FetchTiming.IMMEDIATE,
							attributeSource.isUnWrapProxy(),
							attributeBindingContainer.seekEntityBinding().getEntityName(),
							actualAttribute.getName()
					);
				}
				else {
					return metadata.getTypeResolver().getTypeFactory().specialOneToOne(
							referencedEntityBinding.getEntity().getName(),
							attributeSource.getForeignKeyDirection(),
							uniqueKeyAttributeName,
							attributeSource.getFetchTiming() != FetchTiming.IMMEDIATE,
							attributeSource.isUnWrapProxy(),
							attributeBindingContainer.seekEntityBinding().getEntityName(),
							actualAttribute.getName()
					);
				}
			}
		};

		OneToOneAttributeBinding attributeBinding = (OneToOneAttributeBinding) bindToOneAttribute(
				actualAttribute,
				attributeSource,
				toOneAttributeBindingContext
		);
		if ( attributeSource.getForeignKeyDirection() == ForeignKeyDirection.FROM_PARENT ) {
			List<RelationalValueBinding> foreignKeyRelationalValueBindings =
					attributeBinding
							.getContainer()
							.seekEntityBinding()
							.getHierarchyDetails()
							.getEntityIdentifier()
							.getAttributeBinding()
							.getRelationalValueBindings();

			final List<Column> targetColumns = determineForeignKeyTargetColumns(
					attributeBinding.getReferencedEntityBinding(),
					attributeSource
			);
			locateOrCreateForeignKey(
					quotedIdentifier( attributeSource.getExplicitForeignKeyName() ),
					foreignKeyRelationalValueBindings.get( 0 ).getTable(),
					foreignKeyRelationalValueBindings,
					determineForeignKeyTargetTable( attributeBinding.getReferencedEntityBinding(), attributeSource ),
					targetColumns
			);
		}
		return attributeBinding;
	}

	private SingularAssociationAttributeBinding bindToOneAttribute(
			SingularAttribute attribute,
			final ToOneAttributeSource attributeSource,
			final ToOneAttributeBindingContext attributeBindingContext) {

		final ValueHolder<Class<?>> referencedEntityJavaTypeValue = createSingularAttributeJavaType( attribute );
		final EntityBinding referencedEntityBinding = findOrBindEntityBinding(
				referencedEntityJavaTypeValue,
				attributeSource.getReferencedEntityName()
		);

		// Type resolution...
		if ( !attribute.isTypeResolved() ) {
			attribute.resolveType( referencedEntityBinding.getEntity() );
		}

		//now find the referenced attribute binding, either the referenced entity's id attribute or the referenced attribute
		//todo referenced entityBinding null check?
		final SingularAttributeBinding referencedAttributeBinding = determineReferencedAttributeBinding(
				attributeSource,
				referencedEntityBinding
		);
		// todo : currently a chicken-egg problem here between creating the attribute binding and binding its FK values...
		// now we have everything to create the attribute binding
		final SingularAssociationAttributeBinding attributeBinding =
				attributeBindingContext.createToOneAttributeBinding(
						referencedEntityBinding,
						referencedAttributeBinding
				);

		if ( referencedAttributeBinding != referencedEntityBinding.getHierarchyDetails()
				.getEntityIdentifier()
				.getAttributeBinding() ) {
			referencedAttributeBinding.setAlternateUniqueKey( true );
		}

		attributeBinding.setCascadeStyle( determineCascadeStyle( attributeSource.getCascadeStyles() ) );
		attributeBinding.setFetchTiming( attributeSource.getFetchTiming() );
		attributeBinding.setFetchStyle( attributeSource.getFetchStyle() );

		final Type resolvedType =
				attributeBindingContext.resolveEntityType( referencedEntityBinding, referencedAttributeBinding );
		typeHelper.bindHibernateTypeDescriptor(
				attributeBinding.getHibernateTypeDescriptor(),
				attributeSource.getTypeInformation(),
				referencedEntityJavaTypeValue.getValue().getName(),
				resolvedType
		);
		return attributeBinding;
	}

	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ plural attributes binding
	private AbstractPluralAttributeBinding bindPluralAttribute(
			final AttributeBindingContainer attributeBindingContainer,
			final PluralAttributeSource attributeSource) {
		final PluralAttributeSource.Nature nature = attributeSource.getNature();
		if ( attributeSource.getMappedBy() != null ) {
			attributeSource.resolvePluralAttributeElementSource(
					new PluralAttributeElementSourceResolver.PluralAttributeElementSourceResolutionContext() {
						@Override
						public AttributeSource resolveAttributeSource(String referencedEntityName, String mappedBy) {
							return attributeSource( referencedEntityName, mappedBy );
						}
					}
			);
		}
		final PluralAttribute attribute =
				attributeBindingContainer.getAttributeContainer().locatePluralAttribute( attributeSource.getName() );
		final AbstractPluralAttributeBinding attributeBinding;
		switch ( nature ) {
			case BAG:
				attributeBinding = bindBagAttribute( attributeBindingContainer, attributeSource, attribute );
				break;
			case SET:
				attributeBinding = bindSetAttribute( attributeBindingContainer, attributeSource, attribute );
				break;
			case LIST:
				attributeBinding = bindListAttribute(
						attributeBindingContainer,
						(IndexedPluralAttributeSource) attributeSource,
						attribute
				);
				break;
			case MAP:
				attributeBinding = bindMapAttribute(
						attributeBindingContainer,
						attributeSource,
						attribute
				);
				break;
			case ARRAY:
				attributeBinding = bindArrayAttribute(
						attributeBindingContainer,
						(IndexedPluralAttributeSource) attributeSource,
						attribute
				);
				break;
			default:
				throw new NotYetImplementedException( nature.toString() );
		}

		// Must do first -- sorting/ordering can determine the resolved type
		// (ex: Set vs. SortedSet).
		bindSortingAndOrdering( attributeBinding, attributeSource );

		if ( attributeSource.getFilterSources() != null ) {
			for ( final FilterSource filterSource : attributeSource.getFilterSources() ) {
				attributeBinding.addFilterConfiguration( createFilterConfiguration( filterSource, null ) );
			}
		}

		// Note: Collection types do not have a relational model
		attributeBinding.setFetchTiming( attributeSource.getFetchTiming() );
		attributeBinding.setFetchStyle( attributeSource.getFetchStyle() );
		if ( attributeSource.getFetchStyle() == FetchStyle.SUBSELECT ) {
			attributeBindingContainer.seekEntityBinding().setSubselectLoadableCollections( true );
		}
		attributeBinding.setCaching( attributeSource.getCaching() );
		if ( StringHelper.isNotEmpty( attributeSource.getCustomPersisterClassName() ) ) {
			attributeBinding.setExplicitPersisterClass(
					bindingContext().<CollectionPersister>locateClassByName(
							attributeSource.getCustomPersisterClassName()
					)
			);
		}
		attributeBinding.setCustomLoaderName( attributeSource.getCustomLoaderName() );
		attributeBinding.setCustomSqlInsert( attributeSource.getCustomSqlInsert() );
		attributeBinding.setCustomSqlUpdate( attributeSource.getCustomSqlUpdate() );
		attributeBinding.setCustomSqlDelete( attributeSource.getCustomSqlDelete() );
		attributeBinding.setCustomSqlDeleteAll( attributeSource.getCustomSqlDeleteAll() );
		attributeBinding.setWhere( attributeSource.getWhere() );
		attributeBinding.setMutable( attributeSource.isMutable() );
		attributeBinding.setBatchSize( attributeSource.getBatchSize() );
		ReflectedCollectionJavaTypes reflectedCollectionJavaTypes = HibernateTypeHelper.getReflectedCollectionJavaTypes(
				attributeBinding
		);
		switch ( attributeSource.getElementSource().getNature() ) {
			case BASIC:
				bindBasicPluralAttribute( attributeSource, attributeBinding, reflectedCollectionJavaTypes );
				break;
			case ONE_TO_MANY:
				bindOneToManyAttribute( attributeSource, attributeBinding, reflectedCollectionJavaTypes );
				break;
			case MANY_TO_MANY:
				bindManyToManyAttribute( attributeSource, attributeBinding, reflectedCollectionJavaTypes );
				break;
			case AGGREGATE:
				bindPluralAggregateAttribute( attributeSource, attributeBinding, reflectedCollectionJavaTypes );
				break;
			case MANY_TO_ANY:
				//todo??
			default:
				throw bindingContext().makeMappingException(
						String.format(
								"Unknown type of collection element: %s",
								attributeSource.getElementSource().getNature()
						)
				);
		}
		// Cannot resolve plural attribute type until after the element binding is bound.
		final Type resolvedType = typeHelper.resolvePluralType( attributeBinding, attributeSource, nature );
		final HibernateTypeDescriptor hibernateTypeDescriptor = attributeBinding.getHibernateTypeDescriptor();
		typeHelper.bindHibernateTypeDescriptor(
				hibernateTypeDescriptor,
				attributeSource.getTypeInformation(),
				HibernateTypeHelper.defaultCollectionJavaTypeName( reflectedCollectionJavaTypes, attributeSource ),
				resolvedType
		);
		if ( attributeBinding.hasIndex() ) {
			bindPluralAttributeIndex(
					(IndexedPluralAttributeSource) attributeSource,
					(IndexedPluralAttributeBinding) attributeBinding,
					reflectedCollectionJavaTypes
			);
		}
		bindCollectionTablePrimaryKey( attributeBinding, attributeSource );
		metadata.addCollection( attributeBinding );
		return attributeBinding;
	}

	private AbstractPluralAttributeBinding bindBagAttribute(
			final AttributeBindingContainer attributeBindingContainer,
			final PluralAttributeSource attributeSource,
			PluralAttribute attribute) {
		if ( attribute == null ) {
			attribute = attributeBindingContainer.getAttributeContainer().createBag( attributeSource.getName() );
		}
		return attributeBindingContainer.makeBagAttributeBinding(
				attribute,
				pluralAttributeElementNature( attributeSource ),
				determinePluralAttributeKeyReferencedBinding( attributeBindingContainer, attributeSource ),
				propertyAccessorName( attributeSource ),
				attributeSource.isIncludedInOptimisticLocking(),
				createMetaAttributeContext( attributeBindingContainer, attributeSource )
		);
	}

	private AbstractPluralAttributeBinding bindListAttribute(
			final AttributeBindingContainer attributeBindingContainer,
			final IndexedPluralAttributeSource attributeSource,
			PluralAttribute attribute) {
		if ( attribute == null ) {
			attribute = attributeBindingContainer.getAttributeContainer().createList( attributeSource.getName() );
		}
		return attributeBindingContainer.makeListAttributeBinding(
				attribute,
				pluralAttributeElementNature( attributeSource ),
				determinePluralAttributeKeyReferencedBinding( attributeBindingContainer, attributeSource ),
				propertyAccessorName( attributeSource ),
				attributeSource.isIncludedInOptimisticLocking(),
				createMetaAttributeContext( attributeBindingContainer, attributeSource ),
				getSequentialPluralAttributeIndexBase( attributeSource )
		);
	}

	private AbstractPluralAttributeBinding bindArrayAttribute(
			final AttributeBindingContainer attributeBindingContainer,
			final IndexedPluralAttributeSource attributeSource,
			PluralAttribute attribute) {
		if ( attribute == null ) {
			attribute = attributeBindingContainer.getAttributeContainer().createArray( attributeSource.getName() );
		}
		return attributeBindingContainer.makeArrayAttributeBinding(
				attribute,
				pluralAttributeElementNature( attributeSource ),
				determinePluralAttributeKeyReferencedBinding( attributeBindingContainer, attributeSource ),
				propertyAccessorName( attributeSource ),
				attributeSource.isIncludedInOptimisticLocking(),
				createMetaAttributeContext( attributeBindingContainer, attributeSource ),
				getSequentialPluralAttributeIndexBase( attributeSource )
		);
	}

	private int getSequentialPluralAttributeIndexBase(IndexedPluralAttributeSource pluralAttributeSource) {
		final PluralAttributeIndexSource indexedPluralAttributeSource =  pluralAttributeSource.getIndexSource();
		if ( ! SequentialPluralAttributeIndexSource.class.isInstance( indexedPluralAttributeSource ) ) {
			throw new IllegalArgumentException(
					String.format(
							"Expected an argument of type: %s; instead, got %s",
							SequentialPluralAttributeIndexSource.class.getName(),
							indexedPluralAttributeSource.getClass().getName()
					)
			);
		}
		return ( (SequentialPluralAttributeIndexSource) indexedPluralAttributeSource ).base();
	}

	private AbstractPluralAttributeBinding bindMapAttribute(
			final AttributeBindingContainer attributeBindingContainer,
			final PluralAttributeSource attributeSource,
			PluralAttribute attribute) {
		if ( attribute == null ) {
			attribute = attributeBindingContainer.getAttributeContainer().createMap( attributeSource.getName() );
		}
		return attributeBindingContainer.makeMapAttributeBinding(
				attribute,
				pluralAttributeElementNature( attributeSource ),
				pluralAttributeIndexNature( attributeSource ),
				determinePluralAttributeKeyReferencedBinding( attributeBindingContainer, attributeSource ),
				propertyAccessorName( attributeSource ),
				attributeSource.isIncludedInOptimisticLocking(),

				createMetaAttributeContext( attributeBindingContainer, attributeSource )
		);
	}

	private AbstractPluralAttributeBinding bindSetAttribute(
			final AttributeBindingContainer attributeBindingContainer,
			final PluralAttributeSource attributeSource,
			PluralAttribute attribute) {
		if ( attribute == null ) {
			attribute = attributeBindingContainer.getAttributeContainer().createSet( attributeSource.getName() );
		}
		return attributeBindingContainer.makeSetAttributeBinding(
				attribute,
				pluralAttributeElementNature( attributeSource ),
				determinePluralAttributeKeyReferencedBinding( attributeBindingContainer, attributeSource ),
				propertyAccessorName( attributeSource ),
				attributeSource.isIncludedInOptimisticLocking(),
				createMetaAttributeContext( attributeBindingContainer, attributeSource )
		);
	}


	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ collection attributes binding

	private void bindBasicCollectionElement(
			final BasicPluralAttributeElementBinding elementBinding,
			final BasicPluralAttributeElementSource elementSource,
			final String defaultElementJavaTypeName) {
		bindBasicPluralElementRelationalValues( elementSource, elementBinding );
		typeHelper.bindBasicCollectionElementType( elementBinding, elementSource, defaultElementJavaTypeName );
		elementBinding.getPluralAttributeBinding().getAttribute().setElementType(
				bindingContext().makeJavaType( elementBinding.getHibernateTypeDescriptor().getJavaTypeName() )
		);
	}

	private void bindNonAssociationCollectionKey(
			final AbstractPluralAttributeBinding attributeBinding,
			final PluralAttributeSource attributeSource) {
		if ( attributeSource.getElementSource().getNature() != PluralAttributeElementSource.Nature.BASIC &&
				attributeSource.getElementSource().getNature() != PluralAttributeElementSource.Nature.AGGREGATE ) {
			throw new AssertionFailure(
					String.format(
							"Expected basic or aggregate attribute binding; instead got {%s}",
							attributeSource.getElementSource().getNature()
					)
			);
		}
		attributeBinding.getPluralAttributeKeyBinding().setInverse( false );
		TableSpecification collectionTable = createTable(
				attributeSource.getCollectionTableSpecificationSource(),
				new CollectionTableNamingStrategyHelper( attributeBinding )
		);
		if ( StringHelper.isNotEmpty( attributeSource.getCollectionTableComment() ) ) {
			collectionTable.addComment( attributeSource.getCollectionTableComment() );
		}
		if ( StringHelper.isNotEmpty( attributeSource.getCollectionTableCheck() ) ) {
			collectionTable.addCheckConstraint( attributeSource.getCollectionTableCheck() );
		}
		bindCollectionTableForeignKey(
				attributeBinding,
				attributeSource.getKeySource(),
				collectionTable,
				null
		);
	}

	private void bindCompositeCollectionElement(
			final CompositePluralAttributeElementBinding elementBinding,
			final CompositePluralAttributeElementSource elementSource,
			final String defaultElementJavaTypeName) {
		final PluralAttributeBinding pluralAttributeBinding = elementBinding.getPluralAttributeBinding();
		ValueHolder<Class<?>> defaultElementJavaClassReference = null;
		// Create the aggregate type
		// TODO: aggregateName should be set to elementSource.getPath() (which is currently not implemented)
		//       or Binder should define AttributeBindingContainer paths instead.
		String aggregateName = pluralAttributeBinding.getAttribute().getRole() + ".element";
		final Aggregate aggregate;
		if ( elementSource.getClassName() != null ) {
			aggregate = new Aggregate(
					aggregateName,
					elementSource.getClassName(),
					elementSource.getClassReference() != null ?
							elementSource.getClassReference() :
							bindingContext().makeClassReference( elementSource.getClassName() ),
					null
			);
		}
		else {
			defaultElementJavaClassReference = bindingContext().makeClassReference( defaultElementJavaTypeName );
			aggregate = new Aggregate(
					aggregateName,
					defaultElementJavaClassReference.getValue().getName(),
					defaultElementJavaClassReference,
					null
			);
		}
		final SingularAttribute parentAttribute =
				StringHelper.isEmpty( elementSource.getParentReferenceAttributeName() ) ?
						null :
						aggregate.createSingularAttribute( elementSource.getParentReferenceAttributeName() );
		final CompositeAttributeBindingContainer compositeAttributeBindingContainer =
				elementBinding.createCompositeAttributeBindingContainer(
						aggregate,
						createMetaAttributeContext(
								pluralAttributeBinding.getContainer(),
								elementSource.getMetaAttributeSources()
						),
						parentAttribute
				);

		bindAttributes( compositeAttributeBindingContainer, elementSource );
		pluralAttributeBinding.getAttribute().setElementType( aggregate );
		Type resolvedType = metadata.getTypeResolver().getTypeFactory().component(
				new ComponentMetamodel( compositeAttributeBindingContainer, false, false )
		);
		// TODO: binding the HibernateTypeDescriptor should be simplified since we know the class name already
		typeHelper.bindHibernateTypeDescriptor(
				elementBinding.getHibernateTypeDescriptor(),
				aggregate.getClassName(),
				null,
				defaultElementJavaClassReference == null ? null : defaultElementJavaClassReference.getValue().getName(),
				resolvedType
		);
		/**
		 * TODO
		 * don't know why, but see org.hibernate.mapping.Property#getCompositeCascadeStyle
		 *
		 * and not sure if this is the right place to apply this logic, apparently source level is not okay, so here it is, for now.
		 */
		for ( AttributeBinding ab : compositeAttributeBindingContainer.attributeBindings() ) {
			if ( ab.isCascadeable() ) {
				final Cascadeable cascadeable;
				if ( ab.getAttribute().isSingular() ) {
					cascadeable = Cascadeable.class.cast( ab );
				}
				else {
					cascadeable = Cascadeable.class.cast( ( (PluralAttributeBinding) ab ).getPluralAttributeElementBinding() );
				}
				CascadeStyle cascadeStyle = cascadeable.getCascadeStyle();
				if ( cascadeStyle != CascadeStyles.NONE ) {
					elementBinding.setCascadeStyle( CascadeStyles.ALL );
				}
			}
		}
		if ( elementBinding.getCascadeStyle() == null || elementBinding.getCascadeStyle() == CascadeStyles.NONE ) {
			elementBinding.setCascadeStyle( determineCascadeStyle( elementSource.getCascadeStyles() ) );
		}
	}

	private void bindBasicCollectionIndex(
			final IndexedPluralAttributeBinding attributeBinding,
			final BasicPluralAttributeIndexSource attributeSource,
			final String defaultIndexJavaTypeName) {
		final BasicPluralAttributeIndexBinding indexBinding =
				(BasicPluralAttributeIndexBinding) attributeBinding.getPluralAttributeIndexBinding();
		indexBinding.setRelationalValueBindings(
				bindValues(
						attributeBinding.getContainer(),
						attributeSource,
						attributeBinding.getAttribute(),
						attributeBinding.getPluralAttributeKeyBinding().getCollectionTable(),
						attributeBinding.getPluralAttributeElementBinding()
								.getNature() != PluralAttributeElementBinding.Nature.ONE_TO_MANY
				)
		);
		// TODO: create a foreign key if non-inverse and the index is an association

		typeHelper.bindHibernateTypeDescriptor(
				indexBinding.getHibernateTypeDescriptor(),
				attributeSource.explicitHibernateTypeSource(),
				defaultIndexJavaTypeName
		);
		typeHelper.bindJdbcDataType(
				indexBinding.getHibernateTypeDescriptor().getResolvedTypeMapping(),
				indexBinding.getRelationalValueBindings()
		);
		IndexedPluralAttribute indexedPluralAttribute =
				(IndexedPluralAttribute) indexBinding.getIndexedPluralAttributeBinding().getAttribute();
		indexedPluralAttribute.setIndexType(
				bindingContext().makeJavaType( indexBinding.getHibernateTypeDescriptor().getJavaTypeName() )
		);
	}

	private void bindCompositeCollectionIndex(
		final CompositePluralAttributeIndexBinding indexBinding,
		final CompositePluralAttributeIndexSource indexSource,
		final String defaultIndexJavaTypeName) {
		final PluralAttributeBinding pluralAttributeBinding = indexBinding.getIndexedPluralAttributeBinding();
		ValueHolder<Class<?>> defaultElementJavaClassReference = null;
		// Create the aggregate type
		// TODO: aggregateName should be set to elementSource.getPath() (which is currently not implemented)
		//       or Binder should define AttributeBindingContainer paths instead.
		String aggregateName = pluralAttributeBinding.getAttribute().getRole() + ".index";
		final Aggregate aggregate;
		if ( indexSource.getClassName() != null ) {
			aggregate = new Aggregate(
					aggregateName,
					indexSource.getClassName(),
					indexSource.getClassReference() != null ?
							indexSource.getClassReference() :
							bindingContext().makeClassReference( indexSource.getClassName() ),
					null
			);
		}
		else {
			defaultElementJavaClassReference = bindingContext().makeClassReference( defaultIndexJavaTypeName );
			aggregate = new Aggregate(
					aggregateName,
					defaultElementJavaClassReference.getValue().getName(),
					defaultElementJavaClassReference,
					null
			);
		}
		final CompositeAttributeBindingContainer compositeAttributeBindingContainer =
				indexBinding.createCompositeAttributeBindingContainer(
						aggregate,
						null,
						null
				);

		bindAttributes( compositeAttributeBindingContainer, indexSource );
		Type resolvedType = metadata.getTypeResolver().getTypeFactory().component(
				new ComponentMetamodel( compositeAttributeBindingContainer, false, false )
		);
		// TODO: binding the HibernateTypeDescriptor should be simplified since we know the class name already
		typeHelper.bindHibernateTypeDescriptor(
				indexBinding.getHibernateTypeDescriptor(),
				aggregate.getClassName(),
				null,
				defaultElementJavaClassReference == null ? null : defaultElementJavaClassReference.getValue().getName(),
				resolvedType
		);
		IndexedPluralAttribute indexedPluralAttribute =
				(IndexedPluralAttribute) indexBinding.getIndexedPluralAttributeBinding().getAttribute();
		indexedPluralAttribute.setIndexType( aggregate );
	}

	private void bindOneToManyCollectionElement(
			final OneToManyPluralAttributeElementBinding elementBinding,
			final OneToManyPluralAttributeElementSource elementSource,
			final EntityBinding referencedEntityBinding,
			final String defaultElementJavaTypeName) {
		throwExceptionIfNotFoundIgnored( elementSource.isNotFoundAnException() );
		elementBinding.setElementEntityIdentifier(
				referencedEntityBinding.getHierarchyDetails().getEntityIdentifier()
		);

		Type resolvedElementType = metadata.getTypeResolver().getTypeFactory().manyToOne(
				referencedEntityBinding.getEntity().getName(),
				null,
				false,
				false,
				!elementSource.isNotFoundAnException(), //TODO: should be attributeBinding.isIgnoreNotFound(),
				false
		);
		final HibernateTypeDescriptor hibernateTypeDescriptor = elementBinding.getHibernateTypeDescriptor();
		typeHelper.bindHibernateTypeDescriptor(
				hibernateTypeDescriptor,
				referencedEntityBinding.getEntity().getName(),
				null,
				defaultElementJavaTypeName,
				resolvedElementType
		);
		// no need to bind JDBC data types because element is referenced EntityBinding's ID
		elementBinding.setCascadeStyle( determineCascadeStyle( elementSource.getCascadeStyles() ) );
		elementBinding.getPluralAttributeBinding().getAttribute().setElementType( referencedEntityBinding.getEntity() );
	}

	private void bindManyToManyCollectionElement(
			final ManyToManyPluralAttributeElementBinding elementBinding,
			final ManyToManyPluralAttributeElementSource elementSource,
			final EntityBinding referencedEntityBinding,
			final String defaultElementJavaTypeName) {
		throwExceptionIfNotFoundIgnored( elementSource.isNotFoundAnException() );
		final List<Column> targetColumns =
				determineForeignKeyTargetColumns( referencedEntityBinding, elementSource );
		final List<DefaultNamingStrategy> namingStrategies = new ArrayList<DefaultNamingStrategy>( targetColumns.size() );
		for ( final Column targetColumn : targetColumns ) {
			namingStrategies.add(
					new DefaultNamingStrategy() {
						@Override
						public String defaultName() {
							return bindingContext().getNamingStrategy().foreignKeyColumnName(
									elementBinding.getPluralAttributeBinding().getAttribute().getName(),
									referencedEntityBinding.getEntityName(),
									referencedEntityBinding.getPrimaryTableName(),
									targetColumn.getColumnName().getText()
							);
						}
					}
			);
		}

		final TableSpecification collectionTable = elementBinding.getPluralAttributeBinding().getPluralAttributeKeyBinding().getCollectionTable();
		elementBinding.setRelationalValueBindings(
				bindValues(
						elementBinding.getPluralAttributeBinding().getContainer(),
						elementSource,
						elementBinding.getPluralAttributeBinding().getAttribute(),
						collectionTable,
						namingStrategies,
						true
				)
		);
		if ( elementSource.isUnique() ) {
			for ( RelationalValueBinding relationalValueBinding : elementBinding.getRelationalValueBindings() ) {
				if ( ! relationalValueBinding.isDerived() )  {
					( (Column) relationalValueBinding.getValue() ).setUnique( true );
				}
			}
		}
		if ( !elementBinding.getPluralAttributeBinding().getPluralAttributeKeyBinding().isInverse() &&
				!elementBinding.hasDerivedValue() ) {
			locateOrCreateForeignKey(
					quotedIdentifier( elementSource.getExplicitForeignKeyName() ),
					collectionTable,
					elementBinding.getRelationalValueBindings(),
					determineForeignKeyTargetTable( referencedEntityBinding, elementSource ),
					targetColumns
			);
		}

		typeHelper.bindManyToManyAttributeType(
				elementBinding,
				elementSource,
				referencedEntityBinding,
				defaultElementJavaTypeName
		);
		elementBinding.getPluralAttributeBinding().getAttribute().setElementType( referencedEntityBinding.getEntity() );
		elementBinding.setCascadeStyle( determineCascadeStyle( elementSource.getCascadeStyles() ) );
		elementBinding.setManyToManyWhere( elementSource.getWhere() );
		elementBinding.setManyToManyOrderBy( elementSource.getOrder() );
		elementBinding.setFetchImmediately( elementSource.getFetchTiming() == FetchTiming.IMMEDIATE );
		//TODO: initialize filters from elementSource
	}




	private void bindOneToManyCollectionKey(
			final AbstractPluralAttributeBinding attributeBinding,
			final PluralAttributeSource attributeSource,
			final EntityBinding referencedEntityBinding) {
		if ( attributeSource.getElementSource().getNature() != PluralAttributeElementSource.Nature.ONE_TO_MANY ) {
			throw new AssertionFailure(
					String.format(
							"Expected one-to-many attribute binding; instead got {%s}",
							attributeSource.getElementSource().getNature()
					)
			);
		}
		// By definition, a one-to-many can only be on a foreign key, so the
		// colleciton table is the referenced entity bindings primary table.
		final TableSpecification collectionTable = referencedEntityBinding.getPrimaryTable();
		final boolean isInverse = attributeSource.isInverse();
		final PluralAttributeKeyBinding keyBinding = attributeBinding.getPluralAttributeKeyBinding();
		keyBinding.setInverse( isInverse );
		if ( isInverse && StringHelper.isNotEmpty( attributeSource.getMappedBy() ) ) {
			final String mappedBy = attributeSource.getMappedBy();
			SingularAssociationAttributeBinding referencedAttributeBinding =
					(SingularAssociationAttributeBinding) referencedEntityBinding.locateAttributeBindingByPath( mappedBy, true );
			if ( referencedAttributeBinding == null ) {
				throw new NotYetImplementedException( "Referenced columns not used by an attribute binding is not supported yet." );
			}
			keyBinding.setHibernateTypeDescriptor(
					referencedAttributeBinding.getReferencedAttributeBinding()
							.getHibernateTypeDescriptor()
			);
			List<RelationalValueBinding> sourceColumnBindings = referencedAttributeBinding.getRelationalValueBindings();
			List<Column> sourceColumns = new ArrayList<Column>();
			for ( RelationalValueBinding relationalValueBinding : sourceColumnBindings ) {
				Value v = relationalValueBinding.getValue();
				if ( Column.class.isInstance( v ) ) {
					sourceColumns.add( Column.class.cast( v ) );
				}
			}
			for ( ForeignKey fk : referencedEntityBinding.getPrimaryTable().getForeignKeys() ) {
				if ( fk.getSourceColumns().equals( sourceColumns ) ) {
					keyBinding.setCascadeDeleteEnabled( fk.getDeleteRule() == ForeignKey.ReferentialAction.CASCADE );
				}
			}
			keyBinding.setRelationalValueBindings( sourceColumnBindings );
		}
		else {
			bindCollectionTableForeignKey( attributeBinding, attributeSource.getKeySource(), collectionTable, null );
		}

	}

	private void bindManyToManyCollectionKey(
			final AbstractPluralAttributeBinding attributeBinding,
			final PluralAttributeSource attributeSource,
			final EntityBinding referencedEntityBinding) {
		if ( attributeSource.getElementSource().getNature() != PluralAttributeElementSource.Nature.MANY_TO_MANY ) {
			throw new AssertionFailure(
					String.format(
							"Expected many-to-many attribute binding; instead got {%s}",
							attributeSource.getElementSource().getNature()
					)
			);
		}
		final boolean isInverse = attributeSource.isInverse();
		final TableSpecification collectionTable =
				createTable(
						attributeSource.getCollectionTableSpecificationSource(),
						new ManyToManyCollectionTableNamingStrategyHelper(
								attributeBinding,
								isInverse,
								referencedEntityBinding
						)
				);
		final PluralAttributeKeyBinding keyBinding = attributeBinding.getPluralAttributeKeyBinding();
		keyBinding.setInverse( isInverse );
		String oppositeAttributeName = null;
		if ( attributeSource.getMappedBy() == null ) {
			final String path;
			//if ( StringHelper.isEmpty( attributeBinding.getContainer().getPathBase() ) ) {
				path = createAttributePath( attributeBinding );
			//}
			//else {
			//	path = attributeBinding.getContainer().getPathBase() + '.' +  attributeBinding.getAttribute().getName();
			//}
			// determine if there is an association that is mappedBy this one
			EntitySource referencedEntitySource = entitySourcesByName.get( referencedEntityBinding.getEntityName() );

			for ( AttributeSource referencedAttributeSource : referencedEntitySource.attributeSources() ) {
				// TODO: move this somewhere else or index beforehand.
				// TODO: deal with mappedBy w/in CompositeAttributeBindings.
				if ( referencedAttributeSource instanceof PluralAttributeSource) {
					PluralAttributeSource referencedPluralAttributeSource = (PluralAttributeSource) referencedAttributeSource;
					if ( path.equals( referencedPluralAttributeSource.getMappedBy() ) ) {
						oppositeAttributeName = referencedPluralAttributeSource.getName();
					}
				}
			}
		}
		else {
			oppositeAttributeName = attributeSource.getMappedBy();
		}
		bindCollectionTableForeignKey( attributeBinding, attributeSource.getKeySource(), collectionTable, oppositeAttributeName );
	}

	private void bindPluralAttributeIndex(
			final IndexedPluralAttributeSource attributeSource,
			final IndexedPluralAttributeBinding attributeBinding,
			final ReflectedCollectionJavaTypes reflectedCollectionJavaTypes) {
		final String defaultCollectionIndexJavaTypeName =
				HibernateTypeHelper.defaultCollectionIndexJavaTypeName( reflectedCollectionJavaTypes );
		switch ( attributeSource.getIndexSource().getNature() ) {
			case BASIC: {
				bindBasicCollectionIndex(
						attributeBinding,
						(BasicPluralAttributeIndexSource) attributeSource.getIndexSource(),
						defaultCollectionIndexJavaTypeName
				);
				break;
			}
			case AGGREGATE: {
				bindCompositeCollectionIndex(
						(CompositePluralAttributeIndexBinding) attributeBinding.getPluralAttributeIndexBinding(),
						(CompositePluralAttributeIndexSource) attributeSource.getIndexSource(),
						defaultCollectionIndexJavaTypeName
				);
				break;
			}
			default: {
				throw new NotYetImplementedException(
						String.format(
								"%s collection indexes are not supported yet.",
								attributeSource.getIndexSource().getNature()
						)
				);
			}
		}
		if ( attributeBinding.getPluralAttributeElementBinding()
				.getNature() == PluralAttributeElementBinding.Nature.ONE_TO_MANY ) {
			for ( RelationalValueBinding relationalValueBinding : attributeBinding.getPluralAttributeIndexBinding().getRelationalValueBindings() ) {
				if ( Column.class.isInstance( relationalValueBinding.getValue() ) ) {
					// TODO: fix this when column nullability is refactored
					Column column = (Column) relationalValueBinding.getValue();
					column.setNullable( true );
				}
			}
		}
	}

	private void bindPluralAggregateAttribute(
			final PluralAttributeSource attributeSource,
			final AbstractPluralAttributeBinding attributeBinding,
			final ReflectedCollectionJavaTypes reflectedCollectionJavaTypes) {
		bindNonAssociationCollectionKey( attributeBinding, attributeSource );
		bindCompositeCollectionElement(
				(CompositePluralAttributeElementBinding) attributeBinding.getPluralAttributeElementBinding(),
				(CompositePluralAttributeElementSource) attributeSource.getElementSource(),
				HibernateTypeHelper.defaultCollectionElementJavaTypeName( reflectedCollectionJavaTypes )
		);
	}

	private void bindManyToManyAttribute(
			final PluralAttributeSource attributeSource,
			final AbstractPluralAttributeBinding attributeBinding,
			final ReflectedCollectionJavaTypes reflectedCollectionJavaTypes) {
		final ManyToManyPluralAttributeElementSource elementSource =
				(ManyToManyPluralAttributeElementSource) attributeSource.getElementSource();
		final String defaultElementJavaTypeName = HibernateTypeHelper.defaultCollectionElementJavaTypeName(
				reflectedCollectionJavaTypes
		);
		String referencedEntityName =
				elementSource.getReferencedEntityName() != null ?
						elementSource.getReferencedEntityName() :
						defaultElementJavaTypeName;
		if ( referencedEntityName == null ) {
			throw bindingContext().makeMappingException(
					String.format(
							"The mapping for the entity associated with one-to-many attribute (%s) is undefined.",
							createAttributePathQualifiedByEntityName( attributeBinding )
					)
			);
		}
		EntityBinding referencedEntityBinding = findOrBindEntityBinding( referencedEntityName );
		ManyToManyPluralAttributeElementBinding manyToManyPluralAttributeElementBinding = (ManyToManyPluralAttributeElementBinding) attributeBinding
				.getPluralAttributeElementBinding();

		if ( elementSource.getFilterSources() != null ) {
			for ( FilterSource filterSource : elementSource.getFilterSources() ) {
				manyToManyPluralAttributeElementBinding.addFilterConfiguration(
						createFilterConfiguration(
								filterSource,
								null
						)
				);
			}
		}
		bindManyToManyCollectionKey( attributeBinding, attributeSource, referencedEntityBinding );
		bindManyToManyCollectionElement(
				manyToManyPluralAttributeElementBinding,
				(ManyToManyPluralAttributeElementSource) attributeSource.getElementSource(),
				referencedEntityBinding,
				defaultElementJavaTypeName
		);
	}

	private void bindOneToManyAttribute(
			final PluralAttributeSource attributeSource,
			final AbstractPluralAttributeBinding attributeBinding,
			final ReflectedCollectionJavaTypes reflectedCollectionJavaTypes) {
		final OneToManyPluralAttributeElementSource elementSource =
				(OneToManyPluralAttributeElementSource) attributeSource.getElementSource();
		final String defaultElementJavaTypeName = HibernateTypeHelper.defaultCollectionElementJavaTypeName(
				reflectedCollectionJavaTypes
		);
		String referencedEntityName =
				elementSource.getReferencedEntityName() != null ?
						elementSource.getReferencedEntityName() :
						defaultElementJavaTypeName;
		if ( referencedEntityName == null ) {
			throw bindingContext().makeMappingException(
					String.format(
							"The mapping for the entity associated with one-to-many attribute (%s) is undefined.",
							createAttributePathQualifiedByEntityName( attributeBinding )
					)
			);
		}
		EntityBinding referencedEntityBinding = findOrBindEntityBinding( referencedEntityName );
		bindOneToManyCollectionKey( attributeBinding, attributeSource, referencedEntityBinding );
		bindOneToManyCollectionElement(
				(OneToManyPluralAttributeElementBinding) attributeBinding.getPluralAttributeElementBinding(),
				(OneToManyPluralAttributeElementSource) attributeSource.getElementSource(),
				referencedEntityBinding,
				defaultElementJavaTypeName
		);
	}

	private void bindBasicPluralAttribute(
			final PluralAttributeSource attributeSource,
			final AbstractPluralAttributeBinding attributeBinding,
			final ReflectedCollectionJavaTypes reflectedCollectionJavaTypes) {
		bindNonAssociationCollectionKey( attributeBinding, attributeSource );
		bindBasicCollectionElement(
				(BasicPluralAttributeElementBinding) attributeBinding.getPluralAttributeElementBinding(),
				(BasicPluralAttributeElementSource) attributeSource.getElementSource(),
				HibernateTypeHelper.defaultCollectionElementJavaTypeName( reflectedCollectionJavaTypes )
		);
	}


	private void bindSortingAndOrdering(
			final AbstractPluralAttributeBinding attributeBinding,
			final PluralAttributeSource attributeSource) {
		if ( Sortable.class.isInstance( attributeSource ) ) {
			final Sortable sortable = (Sortable) attributeSource;
			attributeBinding.setSorted( sortable.isSorted() );
			if ( sortable.isSorted()
					&& !sortable.getComparatorName().equalsIgnoreCase( "natural" ) ) {
				Class<Comparator<?>> comparatorClass =
						bindingContext().locateClassByName( sortable.getComparatorName() );
				try {
					attributeBinding.setComparator( comparatorClass.newInstance() );
				}
				catch ( Exception error ) {
					throw bindingContext().makeMappingException(
							String.format(
									"Unable to create comparator [%s] for attribute [%s]",
									sortable.getComparatorName(),
									attributeSource.getName()
							),
							error
					);
				}
			}
		}
		if ( Orderable.class.isInstance( attributeSource ) ) {
			final Orderable orderable = (Orderable) attributeSource;
			if ( orderable.isOrdered() ) {
				attributeBinding.setOrderBy( orderable.getOrder() );

			}
		}
	}

	private SingularAttributeBinding determinePluralAttributeKeyReferencedBinding(
			final AttributeBindingContainer attributeBindingContainer,
			final PluralAttributeSource attributeSource) {
		final EntityBinding entityBinding = attributeBindingContainer.seekEntityBinding();
		final JoinColumnResolutionDelegate resolutionDelegate =
				attributeSource.getKeySource().getForeignKeyTargetColumnResolutionDelegate();

		if ( resolutionDelegate == null ) {
			return entityBinding.getHierarchyDetails().getEntityIdentifier().getAttributeBinding();
		}

		AttributeBinding referencedAttributeBinding;
		final String referencedAttributeName = resolutionDelegate.getReferencedAttributeName();
		if ( referencedAttributeName == null ) {
			referencedAttributeBinding = attributeBindingContainer.seekEntityBinding().locateAttributeBinding(
					resolutionDelegate.getJoinColumns( new JoinColumnResolutionContextImpl( entityBinding ) ),
					true
			);
		}
		else {
			referencedAttributeBinding = attributeBindingContainer.locateAttributeBinding( referencedAttributeName );
		}

		if ( referencedAttributeBinding == null ) {
			throw new NotYetImplementedException( "Referenced columns not used by an attribute binding is not supported yet." );
		}
		if ( !referencedAttributeBinding.getAttribute().isSingular() ) {
			throw bindingContext().makeMappingException(
					"Plural attribute key references a plural attribute; it must not be plural: "
							+ referencedAttributeName
			);
		}
		return (SingularAttributeBinding) referencedAttributeBinding;
	}

	private SingularAttributeBinding determineReferencedAttributeBinding(
			final ForeignKeyContributingSource foreignKeyContributingSource,
			final EntityBinding referencedEntityBinding) {
		final JoinColumnResolutionDelegate resolutionDelegate =
				foreignKeyContributingSource.getForeignKeyTargetColumnResolutionDelegate();
		final JoinColumnResolutionContext resolutionContext = resolutionDelegate == null ? null : new JoinColumnResolutionContextImpl(
				referencedEntityBinding
		);
		if ( resolutionDelegate == null ) {
			return referencedEntityBinding.getHierarchyDetails().getEntityIdentifier().getAttributeBinding();
		}

		final String explicitName = resolutionDelegate.getReferencedAttributeName();
		final AttributeBinding referencedAttributeBinding = explicitName != null
				? referencedEntityBinding.locateAttributeBindingByPath( explicitName, true )
				: referencedEntityBinding.locateAttributeBinding(
				resolutionDelegate.getJoinColumns( resolutionContext ),
				true
		);

		if ( referencedAttributeBinding == null ) {
			if ( explicitName != null ) {
				throw bindingContext().makeMappingException(
						String.format(
								"No attribute binding found with name: %s.%s",
								referencedEntityBinding.getEntityName(),
								explicitName
						)
				);
			}
			else {
				throw new NotYetImplementedException(
						"No support yet for referenced join columns unless they correspond with columns bound for an attribute binding."
				);
			}
		}

		if ( !referencedAttributeBinding.getAttribute().isSingular() ) {
			throw bindingContext().makeMappingException(
					String.format(
							"Foreign key references a non-singular attribute [%s]",
							referencedAttributeBinding.getAttribute().getName()
					)
			);
		}
		return (SingularAttributeBinding) referencedAttributeBinding;
	}

	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ relational binding relates methods
	private void bindBasicPluralElementRelationalValues(
			final RelationalValueSourceContainer relationalValueSourceContainer,
			final BasicPluralAttributeElementBinding elementBinding) {
		elementBinding.setRelationalValueBindings(
				bindValues(
						elementBinding.getPluralAttributeBinding().getContainer(),
						relationalValueSourceContainer,
						elementBinding.getPluralAttributeBinding().getAttribute(),
						elementBinding.getPluralAttributeBinding().getPluralAttributeKeyBinding().getCollectionTable(),
						false
				)
		);
	}

	private void bindBasicSetCollectionTablePrimaryKey(final SetBinding attributeBinding) {
		final BasicPluralAttributeElementBinding elementBinding =
				(BasicPluralAttributeElementBinding) attributeBinding.getPluralAttributeElementBinding();
		if ( elementBinding.getNature() != PluralAttributeElementBinding.Nature.BASIC ) {
			throw bindingContext().makeMappingException(
					String.format(
							"Expected a SetBinding with an element of nature Nature.BASIC; instead was %s",
							elementBinding.getNature()
					)
			);
		}
		if ( elementBinding.hasNonNullableValue() ) {
			bindSetCollectionTablePrimaryKey( attributeBinding );
		}
		else {
			// for backward compatibility, allow a set with no not-null
			// element columns, using all columns in the row locater SQL
			// todo: create an implicit not null constraint on all cols?
		}
	}

	private void bindSetCollectionTablePrimaryKey(final SetBinding attributeBinding) {
		final PluralAttributeElementBinding elementBinding = attributeBinding.getPluralAttributeElementBinding();
		final PrimaryKey primaryKey = attributeBinding.getPluralAttributeKeyBinding()
				.getCollectionTable()
				.getPrimaryKey();
		final List<RelationalValueBinding> keyValueBindings =
				attributeBinding.getPluralAttributeKeyBinding().getRelationalValueBindings();
		for ( final RelationalValueBinding keyRelationalValueBinding : keyValueBindings ) {
			primaryKey.addColumn( (Column) keyRelationalValueBinding.getValue() );
		}
		for ( final RelationalValueBinding elementValueBinding : elementBinding.getRelationalValueBindings() ) {
			if ( !elementValueBinding.isDerived() && !elementValueBinding.isNullable() ) {
				primaryKey.addColumn( (Column) elementValueBinding.getValue() );
			}
		}
	}

	private void bindCollectionTableForeignKey(
			final AbstractPluralAttributeBinding attributeBinding,
			final PluralAttributeKeySource keySource,
			final TableSpecification collectionTable,
			final String oppositeAttributeName ) {

		final AttributeBindingContainer attributeBindingContainer = attributeBinding.getContainer();
		final PluralAttributeKeyBinding keyBinding = attributeBinding.getPluralAttributeKeyBinding();

		final List<Column> targetColumns =
				determineForeignKeyTargetColumns(
						attributeBindingContainer.seekEntityBinding(),
						keySource
				);
		final List<DefaultNamingStrategy> namingStrategies = new ArrayList<DefaultNamingStrategy>( targetColumns.size() );
		for ( final Column targetColumn : targetColumns ) {
			namingStrategies.add(
					new DefaultNamingStrategy() {
						@Override
						public String defaultName() {
							final EntityBinding entityBinding = attributeBinding.getContainer().seekEntityBinding();
							return bindingContext().getNamingStrategy().foreignKeyColumnName(
									oppositeAttributeName,
									entityBinding.getEntityName(),
									entityBinding.getPrimaryTableName(),
									targetColumn.getColumnName().getText()
							);
						}
					}
			);
		}

		List<RelationalValueBinding> sourceRelationalBindings =
				bindValues(
						attributeBindingContainer,
						keySource,
						attributeBinding.getAttribute(),
						collectionTable,
						namingStrategies,
						attributeBinding.getPluralAttributeElementBinding()
								.getNature() != PluralAttributeElementBinding.Nature.ONE_TO_MANY
				);
		keyBinding.setRelationalValueBindings( sourceRelationalBindings );
		ForeignKey foreignKey = locateOrCreateForeignKey(
				quotedIdentifier( keySource.getExplicitForeignKeyName() ),
				collectionTable,
				sourceRelationalBindings,
				determineForeignKeyTargetTable( attributeBinding.getContainer().seekEntityBinding(), keySource ),
				targetColumns
		);
		foreignKey.setDeleteRule( keySource.getOnDeleteAction() );
		keyBinding.setCascadeDeleteEnabled( keySource.getOnDeleteAction() == ForeignKey.ReferentialAction.CASCADE );
		final HibernateTypeDescriptor pluralAttributeKeyTypeDescriptor = keyBinding.getHibernateTypeDescriptor();

		pluralAttributeKeyTypeDescriptor.copyFrom(
				keyBinding.getReferencedAttributeBinding()
						.getHibernateTypeDescriptor()
		);
		final Type resolvedKeyType = pluralAttributeKeyTypeDescriptor.getResolvedTypeMapping();

		Iterator<RelationalValueBinding> fkColumnIterator = keyBinding.getRelationalValueBindings().iterator();
		if ( resolvedKeyType.isComponentType() ) {
			ComponentType componentType = (ComponentType) resolvedKeyType;
			for ( Type subType : componentType.getSubtypes() ) {
				typeHelper.bindJdbcDataType( subType, fkColumnIterator.next().getValue() );
			}
		}
		else {
			typeHelper.bindJdbcDataType( resolvedKeyType, fkColumnIterator.next().getValue() );
		}
	}

	/**
	 * TODO : It is really confusing that we have so many different <tt>natures</tt>
	 */
	private void bindCollectionTablePrimaryKey(
			final AbstractPluralAttributeBinding attributeBinding,
			final PluralAttributeSource attributeSource) {
		final PluralAttributeSource.Nature pluralAttributeSourceNature = attributeSource.getNature();
		final PluralAttributeElementSource.Nature pluralElementSourceNature = attributeSource.getElementSource().getNature();
		final PluralAttributeElementBinding.Nature pluralElementBindingNature = attributeBinding.getPluralAttributeElementBinding().getNature();

		//TODO what is this case? it would be really good to add a comment
		if ( pluralElementSourceNature == PluralAttributeElementSource.Nature.ONE_TO_MANY
				|| pluralAttributeSourceNature == PluralAttributeSource.Nature.BAG ) {
			return;
		}
		if ( pluralElementBindingNature == PluralAttributeElementBinding.Nature.BASIC ) {
			switch ( pluralAttributeSourceNature ) {
				case SET:
					bindBasicSetCollectionTablePrimaryKey( (SetBinding) attributeBinding );
					break;
				case LIST:
				case MAP:
				case ARRAY:
					bindIndexedCollectionTablePrimaryKey( (IndexedPluralAttributeBinding) attributeBinding );
					break;
				default:
					throw new NotYetImplementedException(
							String.format( "%s of basic elements is not supported yet.", pluralAttributeSourceNature )
					);
			}
		}
		else if ( pluralElementBindingNature == PluralAttributeElementBinding.Nature.MANY_TO_MANY ) {
			if ( !attributeBinding.getPluralAttributeKeyBinding().isInverse() ) {
				switch ( pluralAttributeSourceNature ) {
					case SET:
						bindSetCollectionTablePrimaryKey( (SetBinding) attributeBinding );
						break;
					case LIST:
					case MAP:
					case ARRAY:
						bindIndexedCollectionTablePrimaryKey( (IndexedPluralAttributeBinding) attributeBinding );
						break;
					default:
						throw new NotYetImplementedException(
								String.format( "Many-to-many %s is not supported yet.", pluralAttributeSourceNature )
						);
				}
			}
		}
	}

	private void bindSubEntityPrimaryKey(
			final EntityBinding entityBinding,
			final EntitySource entitySource) {
		final InheritanceType inheritanceType = entityBinding.getHierarchyDetails().getInheritanceType();
		final EntityBinding superEntityBinding = entityBinding.getSuperEntityBinding();
		if ( superEntityBinding == null ) {
			throw new AssertionFailure( "super entitybinding is null " );
		}
		if ( inheritanceType == InheritanceType.TABLE_PER_CLASS ) {

		}
		if ( inheritanceType == InheritanceType.JOINED ) {
			JoinedSubclassEntitySource subclassEntitySource = (JoinedSubclassEntitySource) entitySource;



			final List<ColumnSource> columnSources = subclassEntitySource.getPrimaryKeyColumnSources();
			final boolean hasPrimaryKeyJoinColumns = CollectionHelper.isNotEmpty( columnSources );
			final List<Column> superEntityBindingPrimaryKeyColumns = superEntityBinding.getPrimaryTable()
					.getPrimaryKey()
					.getColumns();
			final List<Column> sourceColumns = new ArrayList<Column>( superEntityBindingPrimaryKeyColumns.size() );
			for ( int i = 0, size = superEntityBindingPrimaryKeyColumns.size(); i < size; i++ ) {
				final Column superEntityBindingPrimaryKeyColumn = superEntityBindingPrimaryKeyColumns.get( i );
				ColumnSource primaryKeyJoinColumnSource = hasPrimaryKeyJoinColumns && i < columnSources
						.size() ? columnSources.get( i ) : null;
				final String columnName;
				if ( primaryKeyJoinColumnSource != null && StringHelper.isNotEmpty( primaryKeyJoinColumnSource.getName() ) ) {
					columnName = bindingContext().getNamingStrategy()
							.columnName( primaryKeyJoinColumnSource.getName() );
				}
				else {
					columnName = superEntityBindingPrimaryKeyColumn.getColumnName().getText();
				}
				Column column = entityBinding.getPrimaryTable().locateOrCreateColumn( columnName );
				column.setCheckCondition( superEntityBindingPrimaryKeyColumn.getCheckCondition() );
				column.setComment( superEntityBindingPrimaryKeyColumn.getComment() );
				column.setDefaultValue( superEntityBindingPrimaryKeyColumn.getDefaultValue() );
				column.setIdentity( superEntityBindingPrimaryKeyColumn.isIdentity() );
				column.setNullable( superEntityBindingPrimaryKeyColumn.isNullable() );
				column.setReadFragment( superEntityBindingPrimaryKeyColumn.getReadFragment() );
				column.setWriteFragment( superEntityBindingPrimaryKeyColumn.getWriteFragment() );
				column.setUnique( superEntityBindingPrimaryKeyColumn.isUnique() );
				final String sqlType = primaryKeyJoinColumnSource != null && primaryKeyJoinColumnSource.getSqlType() != null ? primaryKeyJoinColumnSource
						.getSqlType() : superEntityBindingPrimaryKeyColumn.getSqlType();
				column.setSqlType( sqlType );
				column.setSize( superEntityBindingPrimaryKeyColumn.getSize() );
				column.setJdbcDataType( superEntityBindingPrimaryKeyColumn.getJdbcDataType() );
				entityBinding.getPrimaryTable().getPrimaryKey().addColumn( column );
				sourceColumns.add( column );
			}
			List<Column> targetColumns =
					determineForeignKeyTargetColumns(
							superEntityBinding,
							subclassEntitySource
					);

			ForeignKey foreignKey = foreignKeyHelper.locateOrCreateForeignKey(
					quotedIdentifier( subclassEntitySource.getExplicitForeignKeyName() ),
					entityBinding.getPrimaryTable(),
					sourceColumns,
					determineForeignKeyTargetTable( superEntityBinding, subclassEntitySource ),
					targetColumns
			);


			if ( subclassEntitySource.isCascadeDeleteEnabled() ) {
				foreignKey.setDeleteRule( ForeignKey.ReferentialAction.CASCADE );
				entityBinding.setCascadeDeleteEnabled( true );
			}

		}
	}


	private TableSpecification locateDefaultTableSpecificationForAttribute(
			final AttributeBindingContainer attributeBindingContainer,
			final SingularAttributeSource attributeSource) {
		return attributeSource.getContainingTableName() == null ?
				attributeBindingContainer.getPrimaryTable() :
				attributeBindingContainer.seekEntityBinding().locateTable( attributeSource.getContainingTableName() );
	}

	private void bindUniqueConstraints(
			final EntityBinding entityBinding,
			final EntitySource entitySource) {
		int uniqueIndexPerTable = 0;
		for ( final ConstraintSource constraintSource : entitySource.getConstraints() ) {
			if ( UniqueConstraintSource.class.isInstance( constraintSource ) ) {
				final TableSpecification table = entityBinding.locateTable( constraintSource.getTableName() );
				uniqueIndexPerTable++;
				final String constraintName = StringHelper.isEmpty( constraintSource.name() )
						? "key" + uniqueIndexPerTable
						: constraintSource.name();
				final UniqueKey uniqueKey = table.getOrCreateUniqueKey( constraintName );
				for ( final String columnName : constraintSource.columnNames() ) {
					uniqueKey.addColumn( table.locateOrCreateColumn( quotedIdentifier( columnName ) ) );
				}
			}
		}
	}

	private List<RelationalValueBinding> bindValues(
			final AttributeBindingContainer attributeBindingContainer,
			final RelationalValueSourceContainer valueSourceContainer,
			final Attribute attribute,
			final TableSpecification defaultTable,
			final boolean forceNonNullable) {
		final List<DefaultNamingStrategy> list = new ArrayList<DefaultNamingStrategy>( 1 );
		list.add(
				new DefaultNamingStrategy() {
					@Override
					public String defaultName() {
						return bindingContext().getNamingStrategy().propertyToColumnName( attribute.getName() );
					}
				}
		);
		return bindValues(
				attributeBindingContainer,
				valueSourceContainer,
				attribute,
				defaultTable,
				list,
				forceNonNullable
		);
	}

	private List<RelationalValueBinding> bindValues(
			final AttributeBindingContainer attributeBindingContainer,
			final RelationalValueSourceContainer valueSourceContainer,
			final Attribute attribute,
			final TableSpecification defaultTable,
			final List<DefaultNamingStrategy> defaultNamingStrategyList,
			final boolean forceNonNullable) {
		final List<RelationalValueBinding> valueBindings = new ArrayList<RelationalValueBinding>();
		final NaturalIdMutability naturalIdMutability = SingularAttributeSource.class.isInstance(
				valueSourceContainer
		) ? SingularAttributeSource.class.cast( valueSourceContainer ).getNaturalIdMutability()
				: NaturalIdMutability.NOT_NATURAL_ID;
		final boolean isNaturalId = naturalIdMutability != NaturalIdMutability.NOT_NATURAL_ID;
		final boolean isImmutableNaturalId = isNaturalId && ( naturalIdMutability == NaturalIdMutability.IMMUTABLE );
		final boolean reallyForceNonNullable = forceNonNullable ; //|| isNaturalId; todo is a natural id column should be not nullable?

		if ( valueSourceContainer.relationalValueSources().isEmpty() ) {
			for ( DefaultNamingStrategy defaultNamingStrategy : defaultNamingStrategyList ) {
				final String columnName =
						quotedIdentifier( defaultNamingStrategy.defaultName() );
				final Column column = defaultTable.locateOrCreateColumn( columnName );



				column.setNullable( !reallyForceNonNullable && valueSourceContainer.areValuesNullableByDefault() );
				if ( isNaturalId ) {
					addUniqueConstraintForNaturalIdColumn( defaultTable, column );
				}
				valueBindings.add(
						new RelationalValueBinding(
								defaultTable,
								column,
								valueSourceContainer.areValuesIncludedInInsertByDefault(),
								valueSourceContainer.areValuesIncludedInUpdateByDefault() && !isImmutableNaturalId
						)
				);
			}

		}
		else {
			final String name = attribute.getName();
			for ( final RelationalValueSource valueSource : valueSourceContainer.relationalValueSources() ) {
				final TableSpecification table =
						valueSource.getContainingTableName() == null
								? defaultTable
								: attributeBindingContainer.seekEntityBinding()
								.locateTable( valueSource.getContainingTableName() );
				if ( valueSource.getNature() == RelationalValueSource.Nature.COLUMN ) {
					final ColumnSource columnSource = (ColumnSource) valueSource;
					Column column = createColumn(
							table,
							columnSource,
							name,
							reallyForceNonNullable,
							valueSourceContainer.areValuesNullableByDefault(),
							true
					);
					if ( isNaturalId ) {
						addUniqueConstraintForNaturalIdColumn( table, column );
					}
					final boolean isIncludedInInsert =
							TruthValue.toBoolean(
									columnSource.isIncludedInInsert(),
									valueSourceContainer.areValuesIncludedInInsertByDefault()
							);
					final boolean isIncludedInUpdate =
							TruthValue.toBoolean(
									columnSource.isIncludedInUpdate(),
									valueSourceContainer.areValuesIncludedInUpdateByDefault()
							);
					valueBindings.add(
							new RelationalValueBinding(
									table,
									column,
									isIncludedInInsert,
									!isImmutableNaturalId && isIncludedInUpdate
							)
					);
				}
				else {
					final DerivedValue derivedValue =
							table.locateOrCreateDerivedValue( ( (DerivedValueSource) valueSource ).getExpression() );
					valueBindings.add( new RelationalValueBinding( table, derivedValue ) );
				}
			}
		}
		return valueBindings;
	}


	private Value buildDiscriminatorRelationValue(
			final RelationalValueSource valueSource,
			final TableSpecification table) {
		if ( valueSource.getNature() == RelationalValueSource.Nature.COLUMN ) {
			return createColumn(
					table,
					(ColumnSource) valueSource,
					bindingContext().getMappingDefaults().getDiscriminatorColumnName(),
					false,
					false,
					false
			);
		}
		else {
			return table.locateOrCreateDerivedValue( ( (DerivedValueSource) valueSource ).getExpression() );
		}
	}

	private Column createColumn(
			final TableSpecification table,
			final ColumnSource columnSource,
			final String defaultName,
			final boolean forceNotNull,
			final boolean isNullableByDefault,
			final boolean isDefaultAttributeName) {
		if ( columnSource.getName() == null && defaultName == null ) {
			throw bindingContext().makeMappingException(
					"Cannot resolve name for column because no name was specified and default name is null."
			);
		}
		final String resolvedColumnName = nameNormalizer.normalizeDatabaseIdentifier(
				columnSource.getName(),
				new ColumnNamingStrategyHelper( defaultName, isDefaultAttributeName )
		);
		final Column column = table.locateOrCreateColumn( resolvedColumnName );
		resolveColumnNullable( columnSource, forceNotNull, isNullableByDefault, column );
		column.setDefaultValue( columnSource.getDefaultValue() );
		column.setSqlType( columnSource.getSqlType() );
		column.setSize( columnSource.getSize() );
		column.setJdbcDataType( columnSource.getDatatype() );
		column.setReadFragment( columnSource.getReadFragment() );
		column.setWriteFragment( columnSource.getWriteFragment() );
		column.setUnique( columnSource.isUnique() );
		column.setCheckCondition( columnSource.getCheckCondition() );
		column.setComment( columnSource.getComment() );
		return column;
	}

	private void resolveColumnNullable(
			final ColumnSource columnSource,
			final boolean forceNotNull,
			final boolean isNullableByDefault,
			final Column column) {
		if ( forceNotNull ) {
			column.setNullable( false );
			if ( columnSource.isNullable() == TruthValue.TRUE ) {
				log.warn(
						String.format(
								"Natural Id column[%s] has explicit set to allow nullable, we have to make it force not null ",
								columnSource.getName()
						)
				);
			}
		}
		else {
			// if the column is already non-nullable, leave it alone
			if ( column.isNullable() ) {
				column.setNullable( TruthValue.toBoolean( columnSource.isNullable(), isNullableByDefault ) );
			}
		}
	}

	private TableSpecification createTable(
			final TableSpecificationSource tableSpecSource,
			final NamingStrategyHelper namingStrategyHelper) {
		return createTable( tableSpecSource, namingStrategyHelper, null );
	}

	private TableSpecification createTable(
			final TableSpecificationSource tableSpecSource,
			final NamingStrategyHelper namingStrategyHelper,
			final Table includedTable) {
		if ( tableSpecSource == null && namingStrategyHelper == null ) {
			throw bindingContext().makeMappingException( "An explicit name must be specified for the table" );
		}
		final boolean isTableSourceNull = tableSpecSource == null;
		final Schema schema = resolveSchema( tableSpecSource );

		TableSpecification tableSpec;
		if ( isTableSourceNull || tableSpecSource instanceof TableSource  ) {
			String explicitName = isTableSourceNull ? null : TableSource.class.cast( tableSpecSource ).getExplicitTableName();
			String tableName = nameNormalizer.normalizeDatabaseIdentifier( explicitName, namingStrategyHelper );
			String logicTableName = TableNamingStrategyHelper.class.cast( namingStrategyHelper ).getLogicalName( bindingContext().getNamingStrategy());
			tableSpec = createTableSpecification( schema, tableName, logicTableName, includedTable );
		}
		else {
			final InLineViewSource inLineViewSource = (InLineViewSource) tableSpecSource;
			tableSpec = schema.createInLineView(
					createIdentifier( inLineViewSource.getLogicalName() ),
					inLineViewSource.getSelectStatement()
			);
		}
		return tableSpec;
	}

	private Schema resolveSchema(final TableSpecificationSource tableSpecSource) {
		final boolean tableSourceNull = tableSpecSource == null;
		final MappingDefaults mappingDefaults = bindingContext().getMappingDefaults();
		final String explicitCatalogName = tableSourceNull ? null : tableSpecSource.getExplicitCatalogName();
		final String explicitSchemaName = tableSourceNull ? null : tableSpecSource.getExplicitSchemaName();
		final Schema.Name schemaName =
				new Schema.Name(
						createIdentifier( explicitCatalogName, mappingDefaults.getCatalogName() ),
						createIdentifier( explicitSchemaName, mappingDefaults.getSchemaName() )
				);
		return metadata.getDatabase().locateSchema( schemaName );
	}

	private TableSpecification createTableSpecification(
			final Schema schema,
			final String tableName,
			final String logicTableName,
			final Table includedTable) {
		final Identifier logicalTableId = createIdentifier( logicTableName );
		final Identifier physicalTableId = createIdentifier( tableName );
		final Table table = schema.locateTable( logicalTableId );
		if ( table != null ) {
			return table;
		}
		TableSpecification tableSpec;
		if ( includedTable == null ) {
			tableSpec = schema.createTable( logicalTableId, physicalTableId );
		}
		else {
			tableSpec = schema.createDenormalizedTable( logicalTableId, physicalTableId, includedTable );
		}
		return tableSpec;
	}


	private List<Column> determineForeignKeyTargetColumns(
			final EntityBinding entityBinding,
			final ForeignKeyContributingSource foreignKeyContributingSource) {

		// TODO: This method, JoinColumnResolutionContext,
		// and JoinColumnResolutionDelegate need re-worked.  There is currently
		// no way to bind to a collection's inverse foreign key.

		final JoinColumnResolutionDelegate fkColumnResolutionDelegate =
				foreignKeyContributingSource.getForeignKeyTargetColumnResolutionDelegate();

		if ( fkColumnResolutionDelegate == null ) {
			return entityBinding.getPrimaryTable().getPrimaryKey().getColumns();
		}
		else {
			final List<Column> columns = new ArrayList<Column>();
			final JoinColumnResolutionContext resolutionContext = new JoinColumnResolutionContextImpl( entityBinding );
			for ( Value relationalValue : fkColumnResolutionDelegate.getJoinColumns( resolutionContext ) ) {
				if ( !Column.class.isInstance( relationalValue ) ) {
					throw bindingContext().makeMappingException(
							"Foreign keys can currently only name columns, not formulas"
					);
				}
				columns.add( (Column) relationalValue );
			}
			return columns;
		}
	}

	private TableSpecification determineForeignKeyTargetTable(
			final EntityBinding entityBinding,
			final ForeignKeyContributingSource foreignKeyContributingSource) {

		final JoinColumnResolutionDelegate fkColumnResolutionDelegate =
				foreignKeyContributingSource.getForeignKeyTargetColumnResolutionDelegate();
		if ( fkColumnResolutionDelegate == null ) {
			return entityBinding.getPrimaryTable();
		}
		else {
			final JoinColumnResolutionContext resolutionContext = new JoinColumnResolutionContextImpl( entityBinding );
			return fkColumnResolutionDelegate.getReferencedTable( resolutionContext );
		}
	}

	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ simple instance helper methods
	private void mapSourcesByName(final EntitySource entitySource) {
		String entityName = entitySource.getEntityName();
		entitySourcesByName.put( entityName, entitySource );
		log.debugf( "Mapped entity source \"%s\"", entityName );
		final String emptyString = "";
		if ( entitySource instanceof RootEntitySource ) {
			RootEntitySource rootEntitySource = (RootEntitySource) entitySource;
			IdentifierSource identifierSource = rootEntitySource.getIdentifierSource();
			switch ( identifierSource.getNature() ) {
				case SIMPLE:
					final AttributeSource identifierAttributeSource =
							( (SimpleIdentifierSource) identifierSource ).getIdentifierAttributeSource();
					mapAttributeSourceByName( entitySource, emptyString, identifierAttributeSource );
					break;
				case NON_AGGREGATED_COMPOSITE:
					final List<SingularAttributeSource> nonAggregatedAttributeSources =
							( (NonAggregatedCompositeIdentifierSource) identifierSource ).getAttributeSourcesMakingUpIdentifier();
					for ( SingularAttributeSource nonAggregatedAttributeSource : nonAggregatedAttributeSources ) {
						mapAttributeSourceByName( entitySource, emptyString, nonAggregatedAttributeSource );
					}
					break;
				case AGGREGATED_COMPOSITE:
					final ComponentAttributeSource aggregatedAttributeSource =
							( (AggregatedCompositeIdentifierSource) identifierSource ).getIdentifierAttributeSource();
					mapAttributeSourceByName( entitySource, emptyString, aggregatedAttributeSource );
					break;
				default:
					throw new AssertionFailure(
							String.format( "Unknown type of identifier: [%s]", identifierSource.getNature() )
					);
			}
		}
		for ( final AttributeSource attributeSource : entitySource.attributeSources() ) {
			mapAttributeSourceByName( entitySource, emptyString, attributeSource );
		}
		for ( final SubclassEntitySource subclassEntitySource : entitySource.subclassEntitySources() ) {
			mapSourcesByName( subclassEntitySource );
		}
	}

	private void mapAttributeSourceByName(EntitySource entitySource, String pathBase, AttributeSource attributeSource) {
		String attributePath = StringHelper.isEmpty( pathBase ) ?
				attributeSource.getName() :
				pathBase + '.' + attributeSource.getName();
		String key = attributeSourcesByNameKey( entitySource.getEntityName(), attributePath );
		attributeSourcesByName.put( key, attributeSource );
		log.debugf(
				"Mapped attribute source \"%s\" for entity source \"%s\"",
				key,
				entitySource.getEntityName()
		);
		if ( attributeSource instanceof ComponentAttributeSource ) {
			for ( AttributeSource subAttributeSource : ( (ComponentAttributeSource) attributeSource ).attributeSources() ) {

				mapAttributeSourceByName(
						entitySource,
						attributePath,
						subAttributeSource
				);
			}
		}
	}

	private void cleanupBindingContext() {
		bindingContexts.pop();
		inheritanceTypes.pop();
		entityModes.pop();
	}

	public LocalBindingContext bindingContext() {
		return bindingContexts.peek();
	}


	private void setupBindingContext(
			final EntityHierarchy entityHierarchy,
			final RootEntitySource rootEntitySource) {
		// Save inheritance type and entity mode that will apply to entire hierarchy
		inheritanceTypes.push( entityHierarchy.getHierarchyInheritanceType() );
		entityModes.push( rootEntitySource.getEntityMode() );
		bindingContexts.push( rootEntitySource.getLocalBindingContext() );
	}

	private String propertyAccessorName(final AttributeSource attributeSource) {
		return propertyAccessorName( attributeSource.getPropertyAccessorName() );
	}

	private String propertyAccessorName(final String propertyAccessorName) {
		return propertyAccessorName == null
				? bindingContext().getMappingDefaults().getPropertyAccessorName()
				: propertyAccessorName;
	}

	private String quotedIdentifier(final String name) {
		return nameNormalizer.normalizeIdentifierQuoting( name );
	}

	private Identifier createIdentifier(final String name){
		return createIdentifier( name, null );
	}

	private Identifier createIdentifier(final String name, final String defaultName) {
		String identifier = StringHelper.isEmpty( name ) ? defaultName : name;
		identifier = quotedIdentifier( identifier );
		return Identifier.toIdentifier( identifier );
	}

	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ static methods
	private static PluralAttributeElementBinding.Nature pluralAttributeElementNature(
			final PluralAttributeSource attributeSource) {
		return PluralAttributeElementBinding.Nature.valueOf( attributeSource.getElementSource().getNature().name() );
	}

	private static PluralAttributeIndexBinding.Nature pluralAttributeIndexNature(
			final PluralAttributeSource attributeSource) {
		if ( !IndexedPluralAttributeSource.class.isInstance( attributeSource ) ) {
			return null;
		}
		return PluralAttributeIndexBinding.Nature.valueOf(
				( (IndexedPluralAttributeSource) attributeSource ).getIndexSource().getNature().name()
		);
	}

	private static void bindIndexedCollectionTablePrimaryKey(
			final IndexedPluralAttributeBinding attributeBinding) {
		final PrimaryKey primaryKey = attributeBinding.getPluralAttributeKeyBinding()
				.getCollectionTable()
				.getPrimaryKey();
		final List<RelationalValueBinding> keyRelationalValueBindings =
				attributeBinding.getPluralAttributeKeyBinding().getRelationalValueBindings();
		final PluralAttributeIndexBinding indexBinding = attributeBinding.getPluralAttributeIndexBinding();
		for ( final RelationalValueBinding keyRelationalValueBinding : keyRelationalValueBindings ) {
			primaryKey.addColumn( (Column) keyRelationalValueBinding.getValue() );
		}
		for ( RelationalValueBinding relationalValueBinding : indexBinding.getRelationalValueBindings() ) {
			if ( !relationalValueBinding.isDerived() ) {
				primaryKey.addColumn( (Column) relationalValueBinding.getValue() );
			}
		}
	}

	private static void markSuperEntityTableAbstractIfNecessary(
			final EntityBinding superEntityBinding) {
		if ( superEntityBinding == null ) {
			return;
		}
		if ( superEntityBinding.getHierarchyDetails().getInheritanceType() != InheritanceType.TABLE_PER_CLASS ) {
			return;
		}
		if ( superEntityBinding.isAbstract() != Boolean.TRUE ) {
			return;
		}
		if ( !Table.class.isInstance( superEntityBinding.getPrimaryTable() ) ) {
			return;
		}
		Table.class.cast( superEntityBinding.getPrimaryTable() ).setPhysicalTable( false );
	}

	private static String getRelativePathFromEntityName(
			final AttributeBinding attributeBinding) {
		return StringHelper.isEmpty(  attributeBinding.getContainer().getPathBase() ) ?
				attributeBinding.getAttribute().getName() :
				attributeBinding.getContainer().getPathBase() + "." + attributeBinding.getAttribute().getName();
	}

	// TODO: should this be moved to CascadeStyles as a static method?
	// TODO: sources already factor in default cascade; should that be done here instead?
	private static CascadeStyle determineCascadeStyle(
			final Iterable<CascadeStyle> cascadeStyles) {
		CascadeStyle cascadeStyleResult;
		List<CascadeStyle> cascadeStyleList = new ArrayList<CascadeStyle>();
		for ( CascadeStyle style : cascadeStyles ) {
			if ( style != CascadeStyles.NONE ) {
				cascadeStyleList.add( style );
			}
		}
		if ( cascadeStyleList.isEmpty() ) {
			cascadeStyleResult = CascadeStyles.NONE;
		}
		else if ( cascadeStyleList.size() == 1 ) {
			cascadeStyleResult = cascadeStyleList.get( 0 );
		}
		else {
			cascadeStyleResult = new CascadeStyles.MultipleCascadeStyle(
					cascadeStyleList.toArray( new CascadeStyle[cascadeStyleList.size()] )
			);
		}
		return cascadeStyleResult;
	}

	private static MetaAttributeContext createMetaAttributeContext(
			final AttributeBindingContainer attributeBindingContainer,
			final AttributeSource attributeSource) {
		return createMetaAttributeContext( attributeBindingContainer, attributeSource.getMetaAttributeSources() );
	}

	private static MetaAttributeContext createMetaAttributeContext(
			final AttributeBindingContainer attributeBindingContainer,
			final Iterable<? extends MetaAttributeSource> metaAttributeSources) {
		return createMetaAttributeContext(
				metaAttributeSources,
				false,
				attributeBindingContainer.getMetaAttributeContext()
		);
	}

	private static MetaAttributeContext createMetaAttributeContext(
			final Iterable<? extends MetaAttributeSource> metaAttributeSources,
			final boolean onlyInheritable,
			final MetaAttributeContext parentContext) {
		final MetaAttributeContext subContext = new MetaAttributeContext( parentContext );
		for ( final MetaAttributeSource metaAttributeSource : metaAttributeSources ) {
			if ( onlyInheritable && !metaAttributeSource.isInheritable() ) {
				continue;
			}
			final String name = metaAttributeSource.getName();
			MetaAttribute metaAttribute = subContext.getLocalMetaAttribute( name );
			if ( metaAttribute == null || metaAttribute == parentContext.getMetaAttribute( name ) ) {
				metaAttribute = new MetaAttribute( name );
				subContext.add( metaAttribute );
			}
			metaAttribute.addValue( metaAttributeSource.getValue() );
		}
		return subContext;
	}

	private static SingularAttribute createSingularAttribute(
			final AttributeBindingContainer attributeBindingContainer,
			final SingularAttributeSource attributeSource) {
		return attributeSource.isVirtualAttribute()
				? attributeBindingContainer.getAttributeContainer()
				.createSyntheticSingularAttribute( attributeSource.getName() )
				: attributeBindingContainer.getAttributeContainer()
				.createSingularAttribute( attributeSource.getName() );
	}

	private AttributeSource attributeSource(final String entityName, final String attributePath) {
		return attributeSourcesByName.get( attributeSourcesByNameKey( entityName, attributePath ) );
	}

	private static String attributeSourcesByNameKey(
			final String entityName,
			final String attributePath) {
		return entityName + '.' + attributePath;
	}

	static String createAttributePathQualifiedByEntityName(final AttributeBinding attributeBinding) {
		final String entityName = attributeBinding.getContainer().seekEntityBinding().getEntityName();
		return entityName + '.' + createAttributePath( attributeBinding );
	}
	static String createAttributePath(final AttributeBinding attributeBinding) {
		return StringHelper.isEmpty( attributeBinding.getContainer().getPathBase() ) ?
				attributeBinding.getAttribute().getName() :
				attributeBinding.getContainer().getPathBase() + '.' + attributeBinding.getAttribute().getName();
	}

	private static ValueHolder<Class<?>> createSingularAttributeJavaType(
			final Class<?> attributeContainerClassReference,
			final String attributeName) {
		ValueHolder.DeferredInitializer<Class<?>> deferredInitializer =
				new ValueHolder.DeferredInitializer<Class<?>>() {
					public Class<?> initialize() {
						return ReflectHelper.reflectedPropertyClass(
								attributeContainerClassReference,
								attributeName
						);
					}
				};
		return new ValueHolder<Class<?>>( deferredInitializer );
	}

	private static ValueHolder<Class<?>> createSingularAttributeJavaType(
			final SingularAttribute attribute) {
		return createSingularAttributeJavaType(
				attribute.getAttributeContainer().getClassReference(),
				attribute.getName()
		);
	}

	private static String interpretIdentifierUnsavedValue(
			final IdentifierSource identifierSource,
			final IdGenerator generator) {
		if ( identifierSource == null ) {
			throw new IllegalArgumentException( "identifierSource must be non-null." );
		}
		if ( generator == null || StringHelper.isEmpty( generator.getStrategy() ) ) {
			throw new IllegalArgumentException( "generator must be non-null and its strategy must be non-empty." );
		}
		String unsavedValue = null;
		if ( identifierSource.getUnsavedValue() != null ) {
			unsavedValue = identifierSource.getUnsavedValue();
		}
		else if ( "assigned".equals( generator.getStrategy() ) ) {
			unsavedValue = "undefined";
		}
		else {
			switch ( identifierSource.getNature() ) {
				case SIMPLE: {
					// unsavedValue = null;
					break;
				}
				case NON_AGGREGATED_COMPOSITE: {
					// The generator strategy should be "assigned" and processed above.
					throw new IllegalStateException(
							String.format(
									"Expected generator strategy for composite ID: 'assigned'; instead it is: %s",
									generator.getStrategy()
							)
					);
				}
				case AGGREGATED_COMPOSITE: {
					// TODO: if the component only contains 1 attribute (when flattened)
					// and it is not an association then null should be returned;
					// otherwise "undefined" should be returned.
					throw new NotYetImplementedException(
							String.format(
									"Unsaved value for (%s) identifier not implemented yet.",
									identifierSource.getNature()
							)
					);
				}
				default: {
					throw new AssertionFailure(
							String.format(
									"Unexpected identifier nature: %s",
									identifierSource.getNature()
							)
					);
				}
			}
		}
		return unsavedValue;
	}

	/**
	 * Apply executors to a single entity hierarchy.
	 *
	 * @param entityHierarchy The entity hierarchy to be binded.
	 *
	 * @return The root {@link EntityBinding} of the entity hierarchy mapping.
	 */
	private void applyToEntityHierarchy(
			final EntityHierarchy entityHierarchy,
			final LocalBindingContextExecutor rootEntityExecutor,
			final LocalBindingContextExecutor subEntityExecutor) {
		final RootEntitySource rootEntitySource = entityHierarchy.getRootEntitySource();
		setupBindingContext( entityHierarchy, rootEntitySource );
		try {
			LocalBindingContextExecutionContext executionContext =
					new LocalBindingContextExecutionContextImpl( rootEntitySource, null );
			rootEntityExecutor.execute( executionContext );
			if ( inheritanceTypes.peek() != InheritanceType.NO_INHERITANCE ) {
				applyToSubEntities( executionContext.getEntityBinding(), rootEntitySource, subEntityExecutor );
			}
		}
		finally {
			cleanupBindingContext();
		}
	}

	private void applyToSubEntities(
			final EntityBinding entityBinding,
			final EntitySource entitySource,
			final LocalBindingContextExecutor subEntityExecutor) {
		for ( final SubclassEntitySource subEntitySource : entitySource.subclassEntitySources() ) {
			applyToSubEntity( entityBinding, subEntitySource, subEntityExecutor );
		}
	}

	private void applyToSubEntity(
			final EntityBinding superEntityBinding,
			final EntitySource entitySource,
			final LocalBindingContextExecutor subEntityExecutor) {
		final LocalBindingContext bindingContext = entitySource.getLocalBindingContext();
		bindingContexts.push( bindingContext );
		try {
			LocalBindingContextExecutionContext executionContext =
					new LocalBindingContextExecutionContextImpl( entitySource, superEntityBinding );
			subEntityExecutor.execute( executionContext );
			applyToSubEntities( executionContext.getEntityBinding(), entitySource, subEntityExecutor );
		}
		finally {
			bindingContexts.pop();
		}
	}

	private static void addUniqueConstraintForNaturalIdColumn(
			final TableSpecification table,
			final Column column) {
		final UniqueKey uniqueKey = table.getOrCreateUniqueKey( table.getLogicalName().getText()+"_UNIQUEKEY" );
		uniqueKey.addColumn( column );
	}

	private interface LocalBindingContextExecutor {
		void execute(LocalBindingContextExecutionContext bindingContextContext);
	}

	private interface LocalBindingContextExecutionContext {
		EntitySource getEntitySource();
		EntityBinding getEntityBinding();
		EntityBinding getSuperEntityBinding();
	}

	private class LocalBindingContextExecutionContextImpl implements LocalBindingContextExecutionContext {
		private final EntitySource entitySource;
		private final EntityBinding superEntityBinding;

		private LocalBindingContextExecutionContextImpl(EntitySource entitySource, EntityBinding superEntityBinding) {
			this.entitySource = entitySource;
			this.superEntityBinding = superEntityBinding;
		}

		@Override
		public EntitySource getEntitySource() {
			return entitySource;
		}
		@Override
		public EntityBinding getEntityBinding() {
			return metadata.getEntityBinding( entitySource.getEntityName() );
		}
		@Override
		public EntityBinding getSuperEntityBinding() {
			return superEntityBinding;
		}
	}

	public static interface DefaultNamingStrategy {

		String defaultName();
	}

	private static interface ToOneAttributeBindingContext {
		SingularAssociationAttributeBinding createToOneAttributeBinding(
				EntityBinding referencedEntityBinding,
				SingularAttributeBinding referencedAttributeBinding
		);

		EntityType resolveEntityType(
				EntityBinding referencedEntityBinding,
				SingularAttributeBinding referencedAttributeBinding
		);
	}

	public class JoinColumnResolutionContextImpl implements JoinColumnResolutionContext {
		private final EntityBinding referencedEntityBinding;


		public JoinColumnResolutionContextImpl(EntityBinding referencedEntityBinding) {
			this.referencedEntityBinding = referencedEntityBinding;
		}

		@Override
		public Column resolveColumn(
				String logicalColumnName,
				String logicalTableName,
				String logicalSchemaName,
				String logicalCatalogName) {
			if ( bindingContext().isGloballyQuotedIdentifiers() && !StringHelper.isQuoted( logicalColumnName ) ) {
				logicalColumnName = StringHelper.quote( logicalColumnName );
			}
			return resolveTable( logicalTableName, logicalSchemaName, logicalCatalogName ).locateOrCreateColumn(
					logicalColumnName
			);
		}

		@Override
		public TableSpecification resolveTable(String logicalTableName, String logicalSchemaName, String logicalCatalogName) {
			Identifier tableIdentifier = createIdentifier( logicalTableName );
			if ( tableIdentifier == null ) {
				tableIdentifier = referencedEntityBinding.getPrimaryTable().getLogicalName();
			}

			Schema schema = metadata.getDatabase().getSchema( logicalCatalogName, logicalSchemaName );
			return schema.locateTable( tableIdentifier );
		}

		@Override
		public List<Value> resolveRelationalValuesForAttribute(String attributeName) {
			if ( attributeName == null ) {
				List<Value> values = new ArrayList<Value>();
				for ( Column column : referencedEntityBinding.getPrimaryTable().getPrimaryKey().getColumns() ) {
					values.add( column );
				}
				return values;
			}
			List<RelationalValueBinding> valueBindings =
					resolveReferencedAttributeBinding( attributeName ).getRelationalValueBindings();
			List<Value> values = new ArrayList<Value>( valueBindings.size() );
			for ( RelationalValueBinding valueBinding : valueBindings ) {
				values.add( valueBinding.getValue() );
			}
			return values;
		}

		@Override
		public TableSpecification resolveTableForAttribute(String attributeName) {
			if ( attributeName == null ) {
				return referencedEntityBinding.getPrimaryTable();
			}
			else {
				return resolveReferencedAttributeBinding( attributeName ).getRelationalValueBindings().get( 0 ).getTable();
			}
		}

		private SingularAttributeBinding resolveReferencedAttributeBinding(String attributeName) {
			if ( attributeName == null ) {
				return referencedEntityBinding.getHierarchyDetails().getEntityIdentifier().getAttributeBinding();
			}
			final AttributeBinding referencedAttributeBinding =
					referencedEntityBinding.locateAttributeBindingByPath( attributeName, true );
			if ( referencedAttributeBinding == null ) {
				throw bindingContext().makeMappingException(
						String.format(
								"Could not resolve named referenced property [%s] against entity [%s]",
								attributeName,
								referencedEntityBinding.getEntity().getName()
						)
				);
			}
			if ( !referencedAttributeBinding.getAttribute().isSingular() ) {
				throw bindingContext().makeMappingException(
						String.format(
								"Referenced property [%s] against entity [%s] is a plural attribute; it must be a singular attribute.",
								attributeName,
								referencedEntityBinding.getEntity().getName()
						)
				);
			}
			return (SingularAttributeBinding) referencedAttributeBinding;
		}
	}
}