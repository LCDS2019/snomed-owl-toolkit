/*
 * Copyright 2017 SNOMED International, http://snomed.org
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
 *
 * Copyright CSIRO Australian e-Health Research Centre (http://aehrc.com).
 * All rights reserved. Use is subject to license terms and conditions.
 *
 * Original author law223 - initial implementation in Snorocket SNOMED API
 */
package org.snomed.otf.owltoolkit.normalform.internal;

import com.google.common.base.Objects;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.snomed.otf.owltoolkit.classification.ReasonerTaxonomy;
import org.snomed.otf.owltoolkit.domain.Relationship;
import org.snomed.otf.owltoolkit.normalform.RelationshipNormalFormGenerator;
import org.snomed.otf.owltoolkit.normalform.transitive.NodeGraph;
import org.snomed.otf.owltoolkit.ontology.PropertyChain;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents concept attribute-value pairs, used when relationships
 * originating from different sources are being processed.
 */
public final class RelationshipFragment implements SemanticComparable<RelationshipFragment> {

	private RelationshipNormalFormGenerator relationshipNormalFormGenerator;
	private final Relationship fragment;

	/**
	 * Creates a new relationship fragment from the specified relationship.
	 *
	 * @param fragment
	 *            the relationship to extract attribute and value from (may
	 *            not be <code>null</code>)
	 *
	 * @throws NullPointerException
	 *             if the given relationship is <code>null</code>
	 */
	public RelationshipFragment(RelationshipNormalFormGenerator relationshipNormalFormGenerator, final Relationship fragment) {
		this.relationshipNormalFormGenerator = relationshipNormalFormGenerator;
		this.fragment = checkNotNull(fragment, "fragment");
	}

	public boolean isDestinationNegated() {
		return fragment.isDestinationNegated();
	}


	public boolean isUniversal() {
		return fragment.isUniversal();
	}

	public long getTypeId() {
		return fragment.getTypeId();
	}

	public long getModuleId() {
		return fragment.getModuleId();
	}

	public long getDestinationId() {
		return fragment.getDestinationId();
	}


	public long getStatementId() {
		return fragment.getRelationshipId();
	}


	@Override
	public boolean isSameOrStrongerThan(final RelationshipFragment other) {

		if (this.equals(other)) {
			return true;
		}

		if (isUniversal() != other.isUniversal()) {
			return false;
		}

		if (this.getTypeId() == 116680003L && other.getTypeId() == 116680003L) {
			System.out.println("Two IS As compared");
		}

		if (!isDestinationNegated() && !other.isDestinationNegated()) {

			// noinspection UnnecessaryLocalVariable
			RelationshipFragment A = other;
			RelationshipFragment B = this;

			/*
			 * We will return true if A is redundant.
			 *
			 * Rules for determining redundant relationships.
			 *
			 * Rule 1 - Class and Role inclusions
			 * 	Given two relationships, A and B, A with r = C and B with s = D, within the same role group,
			 * 	A is redundant if:
			 * 		r is the same as or a supertype of s, and
			 * 		C is the same as or a supertype of D
			 *
			 * Rule 2 - Property chains including transitive properties
			 * 	Given attribute r, s and t with a property chain SubObjectPropertyOf(ObjectPropertyChain(t s) r),
			 * 	and two relationships A and B, A with r = C and B with u = D, within the same role group,
			 * 	A is redundant if:
			 * 		Attribute u is the same as or a subtype of t, and
			 * 		D has relationship to C via attribute s
			 *
			 */

			final Set<Long> BAttributeClosure = getTransitiveClosure(B.getTypeId());
			final Set<Long> BValueClosure = getTransitiveClosure(B.getDestinationId());

			// Rule 1
			if (BAttributeClosure.contains(A.getTypeId()) && BValueClosure.contains(A.getDestinationId())) {
				return true;
			}

			// Rule 2
			else if (relationshipNormalFormGenerator.isStageOneComplete()) {
				Set<PropertyChain> relevantPropertyChains = relationshipNormalFormGenerator.getPropertyChains().stream()
						.filter(propertyChain -> BAttributeClosure.contains(propertyChain.getSourceType()))
						.filter(propertyChain -> propertyChain.getInferredType().equals(A.getTypeId()))
						.collect(Collectors.toSet());
				for (PropertyChain propertyChain : relevantPropertyChains) {
					if (getPropertyChainTransitiveClosure(B.getDestinationId(), propertyChain.getDestinationType())
							.contains(A.getDestinationId())) {
						return true;
					}
				}
			}
			return false;

		// TODO: Remove all negation logic - Snomed International does not use it.
		} else if (isDestinationNegated() && !other.isDestinationNegated()) {

			final Set<Long> otherAttributeClosure = getTransitiveClosure(other.getTypeId());
			final Set<Long> superTypes = getTransitiveClosure(getDestinationId());
			superTypes.remove(getDestinationId());

			/*
			 * Note that "other" itself may be exhaustive in this case --
			 * the negation will work entirely within the confines of
			 * "other", so it is still going to be more expressive than
			 * "other".
			 *
			 * Supertypes of the negated value can only appear above the
			 * "layers" of exhaustive concepts, because any other case
			 * should be unsatisfiable.
			 */
			return otherAttributeClosure.contains(getTypeId()) && (hasCommonExhaustiveSuperType(other) || isDestinationExhaustive()) && superTypes.contains(other.getDestinationId());

		} else if (!isDestinationNegated() && other.isDestinationNegated()) {

			final Set<Long> attributeClosure = getTransitiveClosure(getTypeId());

			/*
			 * Any contradictions should be filtered out by the reasoner beforehand, so we just check if the two concepts
			 * have a common exhaustive ancestor.
			 */
			return attributeClosure.contains(other.getTypeId()) && hasCommonExhaustiveSuperType(other);

		} else /* if (destinationNegated && other.destinationNegated) */ {

			/*
			 * Note that the comparison is the exact opposite of the first case - if both fragments are negated,
			 * the one which negates a more loose definition is the one that is more strict in the end.
			 */
			final Set<Long> otherAttributeClosure = getTransitiveClosure(other.getTypeId());
			final Set<Long> otherValueClosure = getTransitiveClosure(other.getDestinationId());

			return otherAttributeClosure.contains(getTypeId()) && otherValueClosure.contains(getDestinationId());
		}
	}

	private boolean isDestinationExhaustive() {
		return isExhaustive(getDestinationId());
	}

	private boolean hasCommonExhaustiveSuperType(final RelationshipFragment other) {

		final Set<Long> valueAncestors = relationshipNormalFormGenerator.getReasonerTaxonomy().getAncestors(getDestinationId());
		final Set<Long> otherValueAncestors = relationshipNormalFormGenerator.getReasonerTaxonomy().getAncestors(other.getDestinationId());
		final Set<Long> commonAncestors = Sets.intersection(valueAncestors, otherValueAncestors);

		for (Long commonAncestor : commonAncestors) {
			if (isExhaustive(commonAncestor)) {
				return true;
			}
		}

		return false;
	}

	private boolean isExhaustive(final long conceptId) {
		return relationshipNormalFormGenerator.getSnomedTaxonomy().isExhaustive(conceptId);
	}

	/**
	 * Collects all parent concepts reachable from the specified concept. The
	 * returned set also includes the starting concept.
	 *
	 * @param conceptId
	 *            the concept to start from
	 *
	 * @return a set containing the starting concept and all reachable
	 *         supertypes
	 */
	private Set<Long> getTransitiveClosure(final long conceptId) {
		final Set<Long> ancestors = relationshipNormalFormGenerator.getReasonerTaxonomy().getAncestors(conceptId);
		return Sets.union(ancestors, Collections.singleton(conceptId));
	}

	private static final Map<Long, Map<Long, Set<Long>>> propertyChainTransitiveClosureCache = new Long2ObjectOpenHashMap<>();

	private Set<Long> getPropertyChainTransitiveClosure(final long conceptId, Long chainDestinationType) {
		// Build closure containing all possible hops using chainDestinationType
		// For every concept found also add its super types

		return propertyChainTransitiveClosureCache
				.computeIfAbsent(conceptId, id -> new Long2ObjectOpenHashMap<>())
				.computeIfAbsent(chainDestinationType, type -> {
					NodeGraph nodeGraph = relationshipNormalFormGenerator.getTransitiveNodeGraphs().getOrDefault(chainDestinationType, new NodeGraph());
					ReasonerTaxonomy reasonerTaxonomy = relationshipNormalFormGenerator.getReasonerTaxonomy();

					Set<Long> chainPaths = new LongOpenHashSet();
					chainPaths.add(conceptId);
					chainPaths.addAll(nodeGraph.getAncestors(conceptId));
					Set<Long> chainStepAncestors = new LongOpenHashSet();
					for (Long chainNode : chainPaths) {
						chainStepAncestors.addAll(reasonerTaxonomy.getAncestors(chainNode));
					}
					return Sets.union(chainPaths, chainStepAncestors);
				});
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}

		if (!(obj instanceof RelationshipFragment)) {
			return false;
		}

		final RelationshipFragment other = (RelationshipFragment) obj;

		return (isUniversal() == other.isUniversal()) &&
				(isDestinationNegated() == other.isDestinationNegated()) &&
				(getTypeId() == other.getTypeId()) &&
				(getDestinationId() == other.getDestinationId());
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(isUniversal(), isDestinationNegated(), getTypeId(), getDestinationId());
	}

	@Override
	public String toString() {
		return MessageFormat.format("{0,number,#} : {1}{2,number,#} ({3})", getTypeId(), (isDestinationNegated() ? "NOT" : ""), getDestinationId(), isUniversal());
	}
}
