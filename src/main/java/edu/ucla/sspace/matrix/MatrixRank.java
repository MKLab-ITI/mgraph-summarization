package edu.ucla.sspace.matrix;

import edu.ucla.sspace.vector.DoubleVector;


/**
 * This interface is for any algorithm that ranks a set of nodes in a graph
 * represented by an affinity matrix.  Given some affinity matrix,
 * implementations will use the link structure of the graph to determine which
 * nodes are "important" to the graph.  Measures of important will differ with
 * each implementation, but generally favor nodes that are strongly connected to
 * many other nodes. Two examples are {@link PageRank} and {@link DivRank}. 
 *
 * </br>
 *
 * Any algorithm parameters should be passed in through the constructor of each
 * individual {@link MatrixRank} implementation.
 *
 * @author Keith Stevens
 */
public interface MatrixRank {

    /**
     * Returns a ranking of each node in the graph represented by {@code
     * affinityMatrix}.  Cell values (i, j) are interpreted as the directed edge
     * weight from node i and node j.  The weights in {@code affinityMatrix} can
     * be symmetric or asymmetric, and they do not need to be normalized.  This
     * interface only supports a {@link SparseMatrix} for {@code affinityMatrix}
     * due to the inherit complexity of computing a ranking, and under the
     * assumption that graphs built from word spaces will be sparse.
     *
     * @param affinityMatrix An affinity matrix recording the edge weights
     *        between nodes in a graph.
     * @param initialRanks An initial ranking of nodes in the graph.  Algorithms
     *        may optionally use values in this vector.
     * @return A {@link DoubleVector} of length {@affinityMatrix.rows()} with a
     *         ranking for each node in the graph.
     */
    DoubleVector rankMatrix(SparseMatrix affinityMatrix,
                            DoubleVector initialRanks);

    /**
     * Returns a default weighting for nodes in an {@code affinityMatrix}.  This
     * will most often be an even weighting over all nodes.
     *
     * @param affinityMatrix The matrix over which ranks will be computed.
     */
    DoubleVector defaultRanks(SparseMatrix affinityMatrix);
}
