/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.plan.volcano;

import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.linq4j.function.Predicate1;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptCost;
import org.apache.calcite.plan.RelOptListener;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.plan.RelTrait;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.AbstractRelNode;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelWriter;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.util.ImmutableBitSet;
import org.apache.calcite.util.Util;
import org.apache.calcite.util.trace.CalciteTrace;

import com.google.common.collect.Iterables;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Subset of an equivalence class where all relational expressions have the
 * same physical properties.
 *
 * <p>Physical properties are instances of the {@link RelTraitSet}, and consist
 * of traits such as calling convention and collation (sort-order).
 *
 * <p>For some traits, a relational expression can have more than one instance.
 * For example, R can be sorted on both [X] and [Y, Z]. In which case, R would
 * belong to the sub-sets for [X] and [Y, Z]; and also the leading edges [Y] and
 * [].
 *
 * @see RelNode
 * @see RelSet
 * @see RelTrait
 */
public class RelSubset extends AbstractRelNode {
  //~ Static fields/initializers ---------------------------------------------

  private static final Logger LOGGER = CalciteTrace.getPlannerTracer();

  //~ Instance fields --------------------------------------------------------

  /**
   * cost of best known plan (it may have improved since)
   */
  RelOptCost bestCost;

  /**
   * The set this subset belongs to.
   */
  final RelSet set;

  /**
   * best known plan
   */
  RelNode best;

  /**
   * Timestamp for metadata validity
   */
  long timestamp;

  /**
   * Flag indicating whether this RelSubset's importance was artificially
   * boosted.
   */
  boolean boosted;

  /**
  *Kausik Code.
  */

  List<RelOptCost> costList;
  List<RelNode> nodeList;
  int nodeCount;

  //~ Constructors -----------------------------------------------------------

  RelSubset(
      RelOptCluster cluster,
      RelSet set,
      RelTraitSet traits) {
    super(cluster, traits);
    this.set = set;
    this.boosted = false;
    assert traits.allSimple();
    computeBestCost(cluster.getPlanner());
    recomputeDigest();
  }

  //~ Methods ----------------------------------------------------------------

  /**
   * Computes the best {@link RelNode} in this subset.
   *
   * <p>Only necessary when a subset is created in a set that has subsets that
   * subsume it. Rationale:</p>
   *
   * <ol>
   * <li>If the are no subsuming subsets, the subset is initially empty.</li>
   * <li>After creation, {@code best} and {@code bestCost} are maintained
   *    incrementally by {@link #propagateCostImprovements0} and
   *    {@link RelSet#mergeWith(VolcanoPlanner, RelSet)}.</li>
   * </ol>
   */
  private void computeBestCost(RelOptPlanner planner) {
    bestCost = planner.getCostFactory().makeInfiniteCost();
    for (RelNode rel : getRels()) {
      final RelOptCost cost = planner.getCost(rel);
      if (cost.isLt(bestCost)) {
        bestCost = cost;
        best = rel;
      }
    }
  }

  public Set<String> getVariablesSet() {
    return set.variablesPropagated;
  }

  public Set<String> getVariablesUsed() {
    return set.variablesUsed;
  }

  public RelNode getBest() {
    return best;
  }

  /**
   * Kausik's implementation
   */
  private RelNode getBest(int pos, RelOptPlanner planner) {
    bestCost = planner.getCostFactory().makeInfiniteCost();

    costList = new ArrayList<RelOptCost>();
    nodeList = new ArrayList<RelNode>();

    for (RelNode rel : getRels()) {
      final RelOptCost cost = planner.getCost(rel);
//      System.out.println("Find cost of rel id " + rel.getId() + " cost :" + cost.toString());

      if (costList.size() == 0) {
        costList.add(cost);
        nodeList.add(rel);
      }
      else {
        // Insert cost in the list at the correct place.
        for (int i = 0; i < costList.size(); i++) {
          if(i == 0 && cost.isLt(costList.get(i))) {
            costList.add(i, cost);
            nodeList.add(i, rel);
            break;
          }
          else if(i == costList.size() - 1) {
            // Last Index.
            costList.add(cost);
            nodeList.add(rel);
            break;
          }
          else if(!cost.isLt(costList.get(i)) && cost.isLt(costList.get(i + 1))){
            costList.add(i + 1, cost);
            nodeList.add(i + 1, rel);
            break;
          }
        }
      }
//      System.out.println("List size is " + costList.size());

      if (cost.isLt(bestCost)) {
        bestCost = cost;
        best = rel;
      }
    }
    if (pos >= nodeList.size()) {
//      System.out.println("Returning rel id "
//        + nodeList.get(nodeList.size() - 1).getId()
//        + " cost :"
//        + costList.get(nodeList.size() - 1).toString());
      return nodeList.get(nodeList.size() - 1);
    }
    else {
//      System.out.println("Returning rel id "
//        + nodeList.get(pos).getId()
//        + " cost :"
//        + costList.get(pos).toString());
      return nodeList.get(pos);
    }
  }

  public int getNodeCount() {
    nodeCount = 0;
    for (RelNode rel : getRels()) {
      nodeCount++;
    }
    return nodeCount;
  }

  public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
    throw new UnsupportedOperationException();
  }

  public RelOptCost computeSelfCost(RelOptPlanner planner) {
    return planner.getCostFactory().makeZeroCost();
  }

  public double getRows() {
    if (best != null) {
      return RelMetadataQuery.getRowCount(best);
    } else {
      return RelMetadataQuery.getRowCount(set.rel);
    }
  }

  public void explain(RelWriter pw) {
    // Not a typical implementation of "explain". We don't gather terms &
    // values to be printed later. We actually do the work.
    String s = getDescription();
    pw.item("subset", s);
    final AbstractRelNode input =
        (AbstractRelNode) Iterables.getFirst(getRels(), null);
    if (input == null) {
      return;
    }
    input.explainTerms(pw);
    pw.done(input);
  }

  protected String computeDigest() {
    StringBuilder digest = new StringBuilder("Subset#");
    digest.append(set.id);
    for (RelTrait trait : traitSet) {
      digest.append('.').append(trait);
    }
    return digest.toString();
  }

  // implement RelNode
  protected RelDataType deriveRowType() {
    return set.rel.getRowType();
  }

  // implement RelNode
  public boolean isDistinct() {
    for (RelNode rel : set.rels) {
      if (rel.isDistinct()) {
        return true;
      }
    }
    return false;
  }

  @Override public boolean isKey(ImmutableBitSet columns) {
    for (RelNode rel : set.rels) {
      if (rel.isKey(columns)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the collection of RelNodes one of whose inputs is in this
   * subset.
   */
  Set<RelNode> getParents() {
    final Set<RelNode> list = new LinkedHashSet<RelNode>();
    for (RelNode parent : set.getParentRels()) {
      for (RelSubset rel : inputSubsets(parent)) {
        if (rel.set == set && rel.getTraitSet().equals(traitSet)) {
          list.add(parent);
        }
      }
    }
    return list;
  }

  /**
   * Returns the collection of distinct subsets that contain a RelNode one
   * of whose inputs is in this subset.
   */
  Set<RelSubset> getParentSubsets(VolcanoPlanner planner) {
    final Set<RelSubset> list = new LinkedHashSet<RelSubset>();
    for (RelNode parent : set.getParentRels()) {
      for (RelSubset rel : inputSubsets(parent)) {
        if (rel.set == set && rel.getTraitSet().equals(traitSet)) {
          list.add(planner.getSubset(parent));
        }
      }
    }
    return list;
  }

  private static List<RelSubset> inputSubsets(RelNode parent) {
    //noinspection unchecked
    return (List<RelSubset>) (List) parent.getInputs();
  }

  /**
   * Returns a list of relational expressions one of whose children is this
   * subset. The elements of the list are distinct.
   */
  public Collection<RelNode> getParentRels() {
    final Set<RelNode> list = new LinkedHashSet<RelNode>();
  parentLoop:
    for (RelNode parent : set.getParentRels()) {
      for (RelSubset rel : inputSubsets(parent)) {
        if (rel.set == set && rel.getTraitSet().equals(traitSet)) {
          list.add(parent);
          continue parentLoop;
        }
      }
    }
    return list;
  }

  RelSet getSet() {
    return set;
  }

  /**
   * Adds expression <code>rel</code> to this subset.
   */
  void add(RelNode rel) {
    if (set.rels.contains(rel)) {
      return;
    }

    VolcanoPlanner planner = (VolcanoPlanner) rel.getCluster().getPlanner();
    if (planner.listener != null) {
      RelOptListener.RelEquivalenceEvent event =
          new RelOptListener.RelEquivalenceEvent(
              planner,
              rel,
              this,
              true);
      planner.listener.relEquivalenceFound(event);
    }

    // If this isn't the first rel in the set, it must have compatible
    // row type.
    if (set.rel != null) {
      if (!RelOptUtil.equal("rowtype of new rel", rel.getRowType(),
          "rowtype of set", getRowType(), true)) {
        throw new AssertionError();
      }
    }
    set.addInternal(rel);
    Set<String> variablesSet = RelOptUtil.getVariablesSet(rel);
    Set<String> variablesStopped = rel.getVariablesStopped();
    if (false) {
      Set<String> variablesPropagated =
          Util.minus(variablesSet, variablesStopped);
      assert set.variablesPropagated.containsAll(variablesPropagated);
      Set<String> variablesUsed = RelOptUtil.getVariablesUsed(rel);
      assert set.variablesUsed.containsAll(variablesUsed);
    }
  }

  /**
   * Recursively builds a tree consisting of the cheapest plan at each node.
   */
  RelNode buildCheapestPlan(VolcanoPlanner planner) {

    // Testing
    RelNode tmp = getBest(1, planner);
//    System.out.println("Get Best 1 is id " + tmp.getId());

    CheapestPlanReplacer replacer = new CheapestPlanReplacer(planner);
    final RelNode cheapest = replacer.visit(this, -1, null);

    if (planner.listener != null) {
      RelOptListener.RelChosenEvent event =
          new RelOptListener.RelChosenEvent(
              planner,
              null);
      planner.listener.relChosen(event);
    }

    return cheapest;
  }

  /**
   * Kausik's implementation of build cheapest plan
   */

  RelNode buildCheapestPlan(VolcanoPlanner planner, int n) {
    final RelNode cheapest;
    if(n == 0 ) {
      OrderedPlanReplacer replacer1 = new OrderedPlanReplacer(planner, n);
      System.out.println("Raajay: Total Subsets = "
          + replacer1.countRelSubsets(this));
      System.out.println("Raajay: Total plans = "
          + replacer1.getTotalPlans(this));

      CheapestPlanReplacer replacer = new CheapestPlanReplacer(planner);
      cheapest = replacer.visit(this, -1, null);
    } else {
      OrderedPlanReplacer replacer = new OrderedPlanReplacer(planner, n);
      cheapest = replacer.visit(this, -1, null);
    }

    if (planner.listener != null) {
      RelOptListener.RelChosenEvent event =
          new RelOptListener.RelChosenEvent(
              planner,
              null);
      planner.listener.relChosen(event);
    }

    return cheapest;
  }

  /**
   * Checks whether a relexp has made its subset cheaper, and if it so,
   * recursively checks whether that subset's parents have gotten cheaper.
   *
   * @param planner   Planner
   * @param rel       Relational expression whose cost has improved
   * @param activeSet Set of active subsets, for cycle detection
   */
  void propagateCostImprovements(
      VolcanoPlanner planner,
      RelNode rel,
      Set<RelSubset> activeSet) {
    for (RelSubset subset : set.subsets) {
      if (rel.getTraitSet().satisfies(subset.traitSet)) {
        subset.propagateCostImprovements0(planner, rel, activeSet);
      }
    }
  }

  void propagateCostImprovements0(
      VolcanoPlanner planner,
      RelNode rel,
      Set<RelSubset> activeSet) {
    ++timestamp;

    if (!activeSet.add(this)) {
      // This subset is already in the chain being propagated to. This
      // means that the graph is cyclic, and therefore the cost of this
      // relational expression - not this subset - must be infinite.
      LOGGER.finer("cyclic: " + this);
      return;
    }
    try {
      final RelOptCost cost = planner.getCost(rel);
      if (cost.isLt(bestCost)) {
        if (LOGGER.isLoggable(Level.FINER)) {
          LOGGER.finer("Subset cost improved: subset [" + this
              + "] cost was " + bestCost + " now " + cost);
        }

        bestCost = cost;
        best = rel;

        // Lower cost means lower importance. Other nodes will change
        // too, but we'll get to them later.
        planner.ruleQueue.recompute(this);
        for (RelNode parent : getParents()) {
          final RelSubset parentSubset = planner.getSubset(parent);
          parentSubset.propagateCostImprovements(
              planner, parent, activeSet);
        }
        planner.checkForSatisfiedConverters(set, rel);
      }
    } finally {
      activeSet.remove(this);
    }
  }

  public void propagateBoostRemoval(VolcanoPlanner planner) {
    planner.ruleQueue.recompute(this);

    if (boosted) {
      boosted = false;

      for (RelSubset parentSubset : getParentSubsets(planner)) {
        parentSubset.propagateBoostRemoval(planner);
      }
    }
  }

  public void collectVariablesUsed(Set<String> variableSet) {
    variableSet.addAll(getVariablesUsed());
  }

  public void collectVariablesSet(Set<String> variableSet) {
    variableSet.addAll(getVariablesSet());
  }

  /**
   * Returns the rel nodes in this rel subset.  All rels must have the same
   * traits and are logically equivalent.
   *
   * @return all the rels in the subset
   */
  public Iterable<RelNode> getRels() {
    return new Iterable<RelNode>() {
      public Iterator<RelNode> iterator() {
        return Linq4j.asEnumerable(set.rels)
            .where(
                new Predicate1<RelNode>() {
                  public boolean apply(RelNode v1) {
                    return v1.getTraitSet().satisfies(traitSet);
                  }
                })
            .iterator();
      }
    };
  }

  /**
   * As {@link #getRels()} but returns a list.
   */
  public List<RelNode> getRelList() {
    final List<RelNode> list = new ArrayList<RelNode>();
    for (RelNode rel : set.rels) {
      if (rel.getTraitSet().satisfies(traitSet)) {
        list.add(rel);
      }
    }
    return list;
  }

  //~ Inner Classes ----------------------------------------------------------

  /**
   * Visitor which walks over a tree of {@link RelSet}s, replacing each node
   * with the cheapest implementation of the expression.
   */
  static class CheapestPlanReplacer {
    VolcanoPlanner planner;

    CheapestPlanReplacer(VolcanoPlanner planner) {
      super();
      this.planner = planner;
    }

    public RelNode visit(
        RelNode p,
        int ordinal,
        RelNode parent) {
      if (p instanceof RelSubset) {
        RelSubset subset = (RelSubset) p;
        RelNode cheapest = subset.best;
        if (cheapest == null) {
          // Dump the planner's expression pool so we can figure
          // out why we reached impasse.
          StringWriter sw = new StringWriter();
          final PrintWriter pw = new PrintWriter(sw);
          pw.println("Node [" + subset.getDescription()
              + "] could not be implemented; planner state:\n");
          planner.dump(pw);
          pw.flush();
          final String dump = sw.toString();
          RuntimeException e =
              new RelOptPlanner.CannotPlanException(dump);
          LOGGER.throwing(getClass().getName(), "visit", e);
          throw e;
        }
        p = cheapest;
      }

      if (ordinal != -1) {
        if (planner.listener != null) {
          RelOptListener.RelChosenEvent event =
              new RelOptListener.RelChosenEvent(
                  planner,
                  p);
          planner.listener.relChosen(event);
        }
      }

      List<RelNode> oldInputs = p.getInputs();
      List<RelNode> inputs = new ArrayList<RelNode>();
      for (int i = 0; i < oldInputs.size(); i++) {
        RelNode oldInput = oldInputs.get(i);
        RelNode input = visit(oldInput, i, p);
        inputs.add(input);
      }
      if (!inputs.equals(oldInputs)) {
        final RelNode pOld = p;
        p = p.copy(p.getTraitSet(), inputs);
        planner.provenanceMap.put(
            p, new VolcanoPlanner.DirectProvenance(pOld));
      }
      return p;
    }
  }

  /**
   *
   */
  static class OrderedPlanReplacer {
    VolcanoPlanner planner;
    int rank;

    /**
     * Constructor. Focusses on a rank
     *
     * @param planner
     * @param n
     */
    OrderedPlanReplacer(VolcanoPlanner planner, int n) {
      super();
      this.planner = planner;
      rank = n;
    }

    /**
     * @param root
     * @return The total number of RelSubsets in the lattice.
     */
    public int countRelSubsets(RelNode root) {
      Set<RelSubset> visited = new HashSet<RelSubset>();
      visited = gatherSubsets(root, visited);
      return visited.size();
    }

    /**
     *
     *
     * @param root
     * @return An upper bound on the total number of plans.
     */
    public long getTotalPlans(RelNode root) {
      System.out.println("Version 1");
      return traverse(root, new HashSet<RelNode>());
    }

    private long traverse(RelNode root, final Set<RelNode> visited) {
      List<RelNode> leads = new ArrayList<RelNode>();
      if(root instanceof RelSubset) {
        for(RelNode node: ((RelSubset)root).getRels()) {
          leads.add(node);
        }
      } else {
        leads.add(root);
      }

      if(leads.size() == 0) {
        System.out.println("ERROR: leads is zero in traverse");
      }

      long count = 0;
      for (RelNode node: leads) {
        if(node instanceof TableScan) {
          count += 1;
        } else if(visited.contains(node)) {
          continue; // do not pursue this lead
        } else {
          List<RelNode> inputs = node.getInputs();
          if(inputs.size() == 0) {
            System.out.println("ERROR: inputs is zero in traverse");
          } else {
            // create a duplicate of the current visited state
            Set<RelNode> duplicate = new HashSet<RelNode>(visited);
            duplicate.add(node);
            for(RelNode input: node.getInputs()) {
              count += traverse(input, duplicate);
            }
          }
        }
      }
      return count;
    }

    /**
     *
     *
     * @param curr
     * @param visited
     * @return All the distinct RelSubsets in the lattice.
     */
    private Set<RelSubset> gatherSubsets (RelNode curr, Set<RelSubset> visited) {
      if (visited.contains(curr))
        return visited; // return if this object has already been visited

      List<RelNode> candidates = new ArrayList<RelNode>();
      /*
       * If current is an instance of a subset, we explore leads from all the
       * rels of the subset, if not, we explore the current RelNode
       */
      if(curr instanceof RelSubset) {
        RelSubset subset = (RelSubset) curr;
        visited.add(subset); // mark as visited
        for(RelNode node: subset.getRels()) {
          candidates.add(node);
        }
      } else {
        candidates.add(curr);
      }

      for(RelNode node: candidates) {
        List<RelNode> oldInputs = node.getInputs();
        for (int i = 0; i < oldInputs.size(); i++) {
          gatherSubsets(oldInputs.get(i), visited);
        }
      }
      return visited;
    }


    public RelNode visit(RelNode p, int ordinal, RelNode parent) {

      if (p instanceof RelSubset) {
        RelSubset subset = (RelSubset) p;
        RelNode cheapest = subset.getBest(rank, planner);
        rank = rank - subset.getNodeCount();

        if(rank <= 0) {
          // If sort Order becomes negative, set to zero.
          rank = 0;
        }

        if (cheapest == null) {
          // Dump the planner's expression pool so we can figure
          // out why we reached impasse.
          StringWriter sw = new StringWriter();
          final PrintWriter pw = new PrintWriter(sw);
          pw.println("Node [" + subset.getDescription()
              + "] could not be implemented; planner state:\n");
          planner.dump(pw);
          pw.flush();
          final String dump = sw.toString();
          RuntimeException e =
              new RelOptPlanner.CannotPlanException(dump);
          LOGGER.throwing(getClass().getName(), "visit", e);
          throw e;
        }
        p = cheapest;
      }

      if (ordinal != -1) {
        if (planner.listener != null) {
          RelOptListener.RelChosenEvent event =
              new RelOptListener.RelChosenEvent(
                  planner,
                  p);
          planner.listener.relChosen(event);
        }
      }

      List<RelNode> oldInputs = p.getInputs();
      List<RelNode> inputs = new ArrayList<RelNode>();
      for (int i = 0; i < oldInputs.size(); i++) {
        RelNode oldInput = oldInputs.get(i);
        RelNode input = visit(oldInput, i, p);
        inputs.add(input);
      }
      if (!inputs.equals(oldInputs)) {
        final RelNode pOld = p;
        p = p.copy(p.getTraitSet(), inputs);
        planner.provenanceMap.put(
            p, new VolcanoPlanner.DirectProvenance(pOld));
      }
      return p;
    }
  }
}


// End RelSubset.java
