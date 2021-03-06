package org.drools.compiler.rule.builder.dialect.mvel;

import java.util.HashMap;
import java.util.Map;

import org.drools.compiler.Cheese;
import org.drools.compiler.compiler.BoundIdentifiers;
import org.drools.compiler.compiler.PackageRegistry;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.drools.core.RuleBase;
import org.drools.core.RuleBaseFactory;
import org.drools.core.base.ClassFieldAccessorCache;
import org.drools.core.base.ClassFieldAccessorStore;
import org.drools.core.base.ClassObjectType;
import org.drools.core.base.mvel.MVELPredicateExpression;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.compiler.compiler.AnalysisResult;
import org.drools.compiler.compiler.PackageBuilder;
import org.drools.compiler.compiler.PackageBuilderConfiguration;
import org.drools.compiler.lang.descr.PredicateDescr;
import org.drools.compiler.lang.descr.RuleDescr;
import org.drools.core.reteoo.LeftTupleImpl;
import org.drools.compiler.reteoo.MockLeftTupleSink;
import org.drools.core.rule.Declaration;
import org.drools.core.rule.MVELDialectRuntimeData;
import org.drools.core.rule.Package;
import org.drools.core.rule.Pattern;
import org.drools.core.rule.PredicateConstraint;
import org.drools.core.rule.Rule;
import org.drools.core.rule.PredicateConstraint.PredicateContextEntry;
import org.drools.core.spi.InternalReadAccessor;

public class MVELPredicateBuilderTest {


    ClassFieldAccessorStore store = new ClassFieldAccessorStore();

    @Before
    public void setUp() throws Exception {
        store.setClassFieldAccessorCache( new ClassFieldAccessorCache( Thread.currentThread().getContextClassLoader() ) );
        store.setEagerWire( true );
    }

    @Test
    public void testSimpleExpression() {
        final Package pkg = new Package( "pkg1" );
        final RuleDescr ruleDescr = new RuleDescr( "rule 1" );

        PackageBuilder pkgBuilder = new PackageBuilder( pkg );
        final PackageBuilderConfiguration conf = pkgBuilder.getPackageBuilderConfiguration();
        PackageRegistry pkgRegistry = pkgBuilder.getPackageRegistry( pkg.getName() );
        MVELDialect mvelDialect = ( MVELDialect ) pkgRegistry.getDialectCompiletimeRegistry().getDialect( "mvel" );

        final InstrumentedBuildContent context = new InstrumentedBuildContent( pkgBuilder,
                                                                               ruleDescr,
                                                                               pkgRegistry.getDialectCompiletimeRegistry(),
                                                                               pkg,                                                                               
                                                                               mvelDialect );

        final InstrumentedDeclarationScopeResolver declarationResolver = new InstrumentedDeclarationScopeResolver();
        final InternalReadAccessor extractor = store.getReader( Cheese.class,
                                                             "price",
                                                             getClass().getClassLoader() );

        final Pattern patternA = new Pattern( 0,
                                              new ClassObjectType( Cheese.class ) );

        final Pattern patternB = new Pattern( 1,
                                              new ClassObjectType( Cheese.class ) );

        final Declaration a = new Declaration( "a",
                                               extractor,
                                               patternA );
        final Declaration b = new Declaration( "b",
                                               extractor,
                                               patternB );
        
        context.getBuildStack().add( patternA );
        context.getBuildStack().add( patternB );

        final Map map = new HashMap();
        map.put( "a",
                 a );
        map.put( "b",
                 b );
        declarationResolver.setDeclarations( map );
        context.setDeclarationResolver( declarationResolver );

        final PredicateDescr predicateDescr = new PredicateDescr();
        predicateDescr.setContent( "a == b" );

        
        
        final MVELPredicateBuilder builder = new MVELPredicateBuilder();
        final Declaration[] previousDeclarations = new Declaration[]{a};
        final Declaration[] localDeclarations = new Declaration[]{b};

        final PredicateConstraint predicate = new PredicateConstraint( null,
                                                                       localDeclarations );
        
        AnalysisResult analysis = context.getDialect().analyzeExpression( context,
                                                                          predicateDescr,
                                                                          predicateDescr.getContent(),
                                                                          new BoundIdentifiers( declarationResolver.getDeclarationClasses( (Rule) null ), new HashMap(), null, Cheese.class ) );

        builder.build( context,
                       new BoundIdentifiers( declarationResolver.getDeclarationClasses( (Rule) null ), new HashMap() ),
                       previousDeclarations,
                       localDeclarations,
                       predicate,
                       predicateDescr,
                       analysis );
        
        ( (MVELPredicateExpression) predicate.getPredicateExpression()).compile( (MVELDialectRuntimeData) pkgRegistry.getDialectRuntimeRegistry().getDialectData( "mvel" ) );

        final RuleBase ruleBase = RuleBaseFactory.newRuleBase();
        final InternalWorkingMemory wm = (InternalWorkingMemory) ruleBase.newStatefulSession();

        final Cheese stilton = new Cheese( "stilton",
                                           10 );

        final Cheese cheddar = new Cheese( "cheddar",
                                           10 );
        
        MockLeftTupleSink sink = new MockLeftTupleSink();
        
        final InternalFactHandle f0 = (InternalFactHandle) wm.insert( cheddar );
        final InternalFactHandle f1 = (InternalFactHandle) wm.insert( stilton );
        final LeftTupleImpl tuple = new LeftTupleImpl( f0, sink, true );
        f0.removeLeftTuple(tuple);

        final PredicateContextEntry predicateContext = (PredicateContextEntry) predicate.createContextEntry();
        predicateContext.leftTuple = tuple;
        predicateContext.workingMemory = wm;

        assertTrue( predicate.isAllowedCachedLeft( predicateContext,
                                                   f1 ) );

        cheddar.setPrice( 9 );
        wm.update( f0,
                   cheddar );

        assertFalse( predicate.isAllowedCachedLeft( predicateContext,
                                                    f1 ) );
    }

}
