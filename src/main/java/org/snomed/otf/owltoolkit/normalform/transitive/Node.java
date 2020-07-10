/*
 * Copyright 2017 SNOMED International, http://snomed.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.snomed.otf.owltoolkit.normalform.transitive;

import java.util.HashSet;
import java.util.Set;

public class Node {

	private final Long id;
	private Set<Node> parents;

	public Node(Long id) {
		this.id = id;
		parents = new HashSet<>();
	}

	public Set<Long> getAncestorIds() {
		HashSet<Long> ids = new HashSet<>();
		getAncestorIds(ids);
		ids.remove(id);
		return ids;
	}

	private void getAncestorIds(Set<Long> ids) {
		ids.add(id);
		for (Node parent : parents) {
			parent.getAncestorIds(ids);
		}
	}

	public Set<Node> getParents() {
		return parents;
	}

	public Long getId() {
		return id;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Node)) return false;

		Node node = (Node) o;

		return getId() != null ? getId().equals(node.getId()) : node.getId() == null;
	}

	@Override
	public int hashCode() {
		return getId() != null ? getId().hashCode() : 0;
	}
}
