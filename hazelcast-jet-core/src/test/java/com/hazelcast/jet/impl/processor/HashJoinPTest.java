/*
 * Copyright (c) 2008-2019, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet.impl.processor;

import com.hazelcast.jet.core.JetTestSupport;
import com.hazelcast.jet.core.Processor;
import com.hazelcast.jet.datamodel.ItemsByTag;
import com.hazelcast.jet.datamodel.Tuple2;
import com.hazelcast.jet.datamodel.Tuple3;
import com.hazelcast.jet.function.SupplierEx;
import com.hazelcast.jet.function.TriFunction;
import com.hazelcast.jet.impl.processor.HashJoinCollectP.HashJoinArrayList;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.annotation.ParallelTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.hazelcast.jet.core.test.TestSupport.verifyProcessor;
import static com.hazelcast.jet.datamodel.ItemsByTag.itemsByTag;
import static com.hazelcast.jet.datamodel.Tag.tag0;
import static com.hazelcast.jet.datamodel.Tag.tag1;
import static com.hazelcast.jet.datamodel.Tuple2.tuple2;
import static com.hazelcast.jet.datamodel.Tuple3.tuple3;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

@Category(ParallelTest.class)
@RunWith(HazelcastParallelClassRunner.class)
public class HashJoinPTest extends JetTestSupport {

    private static final BiFunction mapToOutputBiFn = Tuple2::tuple2;
    private static final TriFunction mapToOutputTriFn = Tuple3::tuple3;

    @Test
    public void test_oneToOneJoin_biJoin() {
        SupplierEx<Processor> supplier = () -> new HashJoinP<>(
                singletonList(e -> e),
                emptyList(),
                mapToOutputBiFn,
                mapToOutputTriFn
        );

        verifyProcessor(supplier)
                .disableSnapshots()
                .inputs(asList(
                        asList(0, 1, 2),
                        singletonList(toMap(
                                tuple2(1, "a"),
                                tuple2(2, "b")
                        ))
                ), new int[]{ 10, 1 })
                .expectOutput(asList(
                        tuple2(0, null),
                        tuple2(1, "a"),
                        tuple2(2, "b")
                ));
    }

    @Test
    public void test_oneToNJoin_biJoin() {
        Function<Integer, Object> enrichingSideKeyFn = e -> e % 10;

        SupplierEx<Processor> supplier = () -> new HashJoinP<>(
                singletonList(enrichingSideKeyFn),
                emptyList(),
                mapToOutputBiFn,
                mapToOutputTriFn
        );

        verifyProcessor(supplier)
                .disableSnapshots()
                .inputs(asList(
                        asList(0, 1, 2),
                        singletonList(toMap(
                                tuple2(1, "a"),
                                tuple2(2, asCustomList("b", "c"))
                        ))

                ), new int[]{ 10, 1 })
                .expectOutput(asList(
                        tuple2(0, null),
                        tuple2(1, "a"),
                        tuple2(2, "b"),
                        tuple2(2, "c")
                ));
    }

    @Test
    public void test_oneToOneJoin_triJoin() {
        SupplierEx<Processor> supplier = () -> new HashJoinP<>(
                asList(e -> e, e -> e),
                emptyList(),
                mapToOutputBiFn,
                mapToOutputTriFn
        );

        verifyProcessor(supplier)
                .disableSnapshots()
                .inputs(asList(
                        asList(1, 2, 3),
                        singletonList(toMap(
                                tuple2(1, "a"),
                                tuple2(3, "c")
                        )),
                        singletonList(toMap(
                                tuple2(1, "A"),
                                tuple2(2, "B")
                        ))
                ), new int[]{ 10, 1, 1 })
                .expectOutput(asList(
                        tuple3(1, "a", "A"),
                        tuple3(2, null, "B"),
                        tuple3(3, "c", null)
                ));
    }

    @Test
    public void test_oneToNJoin_triJoin() {
        SupplierEx<Processor> supplier = () -> new HashJoinP<>(
                asList(e -> e, e -> e),
                emptyList(),
                mapToOutputBiFn,
                mapToOutputTriFn
        );

        verifyProcessor(supplier)
                .disableSnapshots()
                .inputs(asList(
                        asList(0, 1, 2, 3, 4),
                        singletonList(toMap(
                                tuple2(1, "a"),
                                tuple2(2, asCustomList("b", "c")),
                                tuple2(4, asCustomList("d", "e"))
                        )),
                        singletonList(toMap(
                                tuple2(2, "A"),
                                tuple2(3, asCustomList("B", "C")),
                                tuple2(4, asCustomList("D", "E"))
                        ))
                ), new int[]{ 10, 1, 1 })
                .expectOutput(asList(
                        tuple3(0, null, null),
                        tuple3(1, "a", null),
                        tuple3(2, "b", "A"),
                        tuple3(2, "c", "A"),
                        tuple3(3, null, "B"),
                        tuple3(3, null, "C"),
                        tuple3(4, "d", "D"),
                        tuple3(4, "d", "E"),
                        tuple3(4, "e", "D"),
                        tuple3(4, "e", "E")
                ));
    }

    @Test
    public void test_oneToOneJoin_withTags() {
        SupplierEx<Processor> supplier = () -> new HashJoinP<>(
                asList(e -> e, e -> e),
                asList(tag0(), tag1()),
                mapToOutputBiFn,
                mapToOutputTriFn
        );

        verifyProcessor(supplier)
                .disableSnapshots()
                .inputs(asList(
                        asList(1, 2, 3),
                        singletonList(toMap(
                                tuple2(1, "a"),
                                tuple2(3, "c")
                        )),
                        singletonList(toMap(
                                tuple2(1, "A"),
                                tuple2(2, "B")
                        ))
                ), new int[]{ 10, 1, 1 })
                .expectOutput(asList(
                        tuple2(1, itemsByTag(tag0(), "a", tag1(), "A")),
                        tuple2(2, itemsByTag(tag0(), null, tag1(), "B")),
                        tuple2(3, itemsByTag(tag0(), "c", tag1(), null))
                ));
    }

    @Test
    public void test_oneToNJoin_withTags() {
        SupplierEx<Processor> supplier = () -> new HashJoinP<>(
                asList(e -> e, e -> e),
                asList(tag0(), tag1()),
                mapToOutputBiFn,
                mapToOutputTriFn
        );

        verifyProcessor(supplier)
                .disableSnapshots()
                .inputs(asList(
                        asList(0, 1, 2, 3, 4),
                        singletonList(toMap(
                                tuple2(1, "a"),
                                tuple2(2, asCustomList("b", "c")),
                                tuple2(4, asCustomList("d", "e"))
                        )),
                        singletonList(toMap(
                                tuple2(2, "A"),
                                tuple2(3, asCustomList("B", "C")),
                                tuple2(4, asCustomList("D", "E"))
                        ))
                ), new int[]{ 10, 1, 1 })
                .expectOutput(asList(
                        tuple2(0, itemsByTag(tag0(), null, tag1(), null)),
                        tuple2(1, itemsByTag(tag0(), "a", tag1(), null)),
                        tuple2(2, itemsByTag(tag0(), "b", tag1(), "A")),
                        tuple2(2, itemsByTag(tag0(), "c", tag1(), "A")),
                        tuple2(3, itemsByTag(tag0(), null, tag1(), "B")),
                        tuple2(3, itemsByTag(tag0(), null, tag1(), "C")),
                        tuple2(4, itemsByTag(tag0(), "d", tag1(), "D")),
                        tuple2(4, itemsByTag(tag0(), "d", tag1(), "E")),
                        tuple2(4, itemsByTag(tag0(), "e", tag1(), "D")),
                        tuple2(4, itemsByTag(tag0(), "e", tag1(), "E"))
                ));
    }

    @Test
    public void test_biJoin_mapToNull() {
        SupplierEx<Processor> supplier = () -> new HashJoinP<>(
                singletonList(e -> e),
                emptyList(),
                (l, r) -> r == null ? null : tuple2(l, r),
                null
        );

        verifyProcessor(supplier)
                .disableSnapshots()
                .inputs(asList(
                        asList(0, 1),
                        singletonList(toMap(
                                tuple2(1, "a")
                        ))
                ), new int[]{ 10, 1 })
                .expectOutput(singletonList(
                        tuple2(1, "a")));
    }

    @Test
    public void test_triJoin_mapToNull() {
        SupplierEx<Processor> supplier = () -> new HashJoinP<>(
                asList(e -> e, e -> e),
                emptyList(),
                null,
                (l, r1, r2) -> r1 == null || r2 == null ? null : tuple3(l, r1, r2)
        );

        verifyProcessor(supplier)
                .disableSnapshots()
                .inputs(asList(
                        asList(0, 1, 2, 3),
                        singletonList(toMap(
                                tuple2(1, "a"),
                                tuple2(2, "b")
                        )),
                        singletonList(toMap(
                                tuple2(1, "A"),
                                tuple2(3, "C")
                        ))
                ), new int[]{ 10, 1, 1 })
                .expectOutput(singletonList(
                        tuple3(1, "a", "A")));
    }

    @Test
    public void test_withTags_mapToNull() {
        SupplierEx<Processor> supplier = () -> new HashJoinP<>(
                asList(e -> e, e -> e),
                asList(tag0(), tag1()),
                (item, map) -> ((ItemsByTag) map).get(tag0()) == null ? null : tuple2(item, map),
                null
        );

        verifyProcessor(supplier)
                .disableSnapshots()
                .inputs(asList(
                        asList(1, 2, 3),
                        singletonList(toMap(
                                tuple2(1, "a"),
                                tuple2(3, "c")
                        )),
                        singletonList(toMap(
                                tuple2(1, "A"),
                                tuple2(2, "B")
                        ))
                ), new int[]{ 10, 1, 1 })
                .expectOutput(asList(
                        tuple2(1, itemsByTag(tag0(), "a", tag1(), "A")),
                        tuple2(3, itemsByTag(tag0(), "c", tag1(), null))
                ));

    }

    @Test
    public void when_arrayListInItems_then_treatedAsAnItem() {
        SupplierEx<Processor> supplier = () -> new HashJoinP<>(
                singletonList(e -> e),
                emptyList(),
                mapToOutputBiFn,
                mapToOutputTriFn
        );

        List<String> listItem = new ArrayList<>();
        listItem.add("a");
        listItem.add("b");
        verifyProcessor(supplier)
                .disableSnapshots()
                .inputs(asList(
                        singletonList(0),
                        singletonList(toMap(
                                tuple2(0, listItem)
                        ))
                ), new int[]{ 10, 1 })
                .expectOutput(singletonList(
                        tuple2(0, listItem)
                ));
    }

    @SafeVarargs
    private static <T> HashJoinArrayList asCustomList(T ... values) {
        HashJoinArrayList res = new HashJoinArrayList();
        Collections.addAll(res, values);
        return res;
    }

    @SafeVarargs
    private static <K, V> Map<K, V> toMap(Tuple2<K, V>... entries) {
        return Stream.of(entries).collect(Collectors.toMap(Tuple2::f0, Tuple2::f1));
    }
}