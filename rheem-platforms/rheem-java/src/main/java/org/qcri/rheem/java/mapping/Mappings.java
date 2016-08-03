package org.qcri.rheem.java.mapping;

import org.qcri.rheem.core.mapping.Mapping;

import java.util.Arrays;
import java.util.Collection;

/**
 * Register for {@link Mapping}s for this platform.
 */
public class Mappings {

    public static Collection<Mapping> BASIC_MAPPINGS = Arrays.asList(
            new TextFileSourceMapping(),
            new MapMapping(),
            new ReduceByMapping(),
            new CollectionSourceMapping(),
            new LocalCallbackSinkMapping(),
            new GlobalReduceMapping(),
            new MaterializedGroupByMapping(),
            new GlobalMaterializedGroupMapping(),
            new FlatMapMapping(),
            new CountMapping(),
            new DistinctMapping(),
            new SortMapping(),
            new FilterMapping(),
            new UnionAllMapping(),
            new IntersectMapping(),
            new CartesianMapping(),
            new JoinMapping(),
            new LoopMapping(),
            new DoWhileMapping(),
            new SampleMapping(),
            new ZipWithIdMapping()
    );

}