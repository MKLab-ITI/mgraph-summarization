/*
 * Copyright 2011 Keith Stevens 
 *
 * This file is part of the S-Space package and is covered under the terms and
 * conditions therein.
 *
 * The S-Space package is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation and distributed hereunder to you.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND NO REPRESENTATIONS OR WARRANTIES,
 * EXPRESS OR IMPLIED ARE MADE.  BY WAY OF EXAMPLE, BUT NOT LIMITATION, WE MAKE
 * NO REPRESENTATIONS OR WARRANTIES OF MERCHANT- ABILITY OR FITNESS FOR ANY
 * PARTICULAR PURPOSE OR THAT THE USE OF THE LICENSED SOFTWARE OR DOCUMENTATION
 * WILL NOT INFRINGE ANY THIRD PARTY PATENTS, COPYRIGHTS, TRADEMARKS OR OTHER
 * RIGHTS.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package edu.ucla.sspace.matrix;

import edu.ucla.sspace.vector.DenseVector;
import edu.ucla.sspace.vector.DoubleVector;
import edu.ucla.sspace.vector.SparseDoubleVector;

/**
 * A {@link MatrixRank} implementation based on the following paper:
 *
 *   <li style="font-family:Garamond, Georgia, serif">Q. Mei, J. Guo, and D.
 *   Radev, "DivRank: the Interplay of Prestige and Diversity in Information
 *   Netorks." <i>Proceedings of the 16th ACM SIGKDD international conference on
 *   Knowledge discovery and data mining</i>, Washington, DC, USA, 2010. </li>
 *
 * </p>
 *
 * This ranking gives high ranks to nodes that are both well connected to the
 * rest of the graph.  In short, if a node connects to many other nodes, and
 * many other nodes connect to it, it will be given a higher page rank.
 * Conversely, if a a node has no outlines, but many nodes connect to it, then
 * it will receive a low rank, since it is a sink.  
 *
 * </p> 
 *
 * Given a sparse affinity matrix, this {@link MatrixRank} implementation will
 * iteratively compute a random walk over the network graph represented by the
 * matrix.  Each row is interpreted as the transitions probabilities from a node
 * in the graph.  To guarantee convergence of the random walk, the given matrix
 * will be row normalized such that each row represents a probabilitiy
 * distribution.  
 *
 * </p>
 *
 * An initial ranking is needed to represent the initial probability of arriving
 * at each node.  These initial rankings are used through the computation for a
 * random restart in each random walk, which helps avoid nodes with no
 * outlinks..  A default ranking of even probabilities for each node is provided
 * through {@link #defaultRanks(SparseMatrix) defaultRanks}.
 *
 * </p>
 *
 * Each instance of {@link DivRank} is threadsafe and can be used to compute
 * ranks over multiple graphs simultaniously.  Each instance uses the same 
 * value for {@code randomJumpWeight}.
 *
 * @author Keith Stevens
 * @author Joey Silva
 */
public class PageRank implements MatrixRank {

    /**
     * The weight factor given to the random jump probabilities versus the
     * actual edge weights in the affinity matrix.
     */
    protected final double randomJumpWeight;

    /**
     * The weight given to ranks computed from a random walk through the
     * affinity matrix.
     */
    protected final double affinityWeight;

    /**
     * Creates a new {@link PageRank}. 
     */
    public PageRank(double randomJumpWeight) {
        this.randomJumpWeight = randomJumpWeight;
        this.affinityWeight = 1 - randomJumpWeight;
    }

    /**
     * {@inherDoc}
     */
    public DoubleVector rankMatrix(SparseMatrix affinityMatrix, 
                                   DoubleVector initialRanks) {
        int numRows = affinityMatrix.rows(); 

        DoubleVector pageRanks = initialRanks;

        scaleAffinityMatrix(affinityMatrix);

        // Iterate over a row, saving the intermediate sum of each row use
        // built-in fn to get sparse row and also getnonzero before summing
        // multiply by position in v at the end.
        for(int k = 0; k < 20; k++) {
            DoubleVector newPageRanks = new DenseVector(numRows);

            // Compute the ranks based on a random walk through the affinity
            // matrix.  Traditionally, this is done by multiplying the transpose
            // of the affinity matrix by the page rank vector.  Since computing
            // the transpose is costly, both memory wise and access wise, we
            // compute it without doing the transpose directly.
            for(int i = 0; i < numRows; i++) {
                SparseDoubleVector row = affinityMatrix.getRowVector(i);
                for(int j:row.getNonZeroIndices())
                    newPageRanks.add(j, row.get(j) * pageRanks.get(i));
            }


            // Scale each computed rank by the factor given to the random jump
            // weight. 
            for(int i = 0; i < numRows; i++)
                newPageRanks.set(i, newPageRanks.get(i) * affinityWeight);

            addRandomJumpProbabilities(newPageRanks, pageRanks, initialRanks);

            // Store the new page rank scores.
            pageRanks = newPageRanks;
        }

        return pageRanks;
    }

    /**
     * Scales the {@code affinityMatrix} such that each row represents a
     * probability distribution of transitions.
     */
    protected void scaleAffinityMatrix(SparseMatrix affinityMatrix) {
        // Normalize the wieght of the outlinks for each node such that they
        // represent a probability distribution, i.e. they sum to 1.
        for(int r = 0; r < affinityMatrix.rows(); r++) {
            SparseDoubleVector row = affinityMatrix.getRowVector(r);

            // Sum the weights of the row.
            double rowSum = 0;
            for (int c : row.getNonZeroIndices())
                rowSum += row.get(c);

            // Normalize by the row sum if it is non-zero.
            if (rowSum == 0d)
                continue;
            for (int c : row.getNonZeroIndices())
                affinityMatrix.set(r, c, row.get(c) / rowSum);
        }
    }

    /**
     * Adds the rank reated by random restart from each node.  This is based on
     * both the {@code randomJumpWeight}, and the difference between the L1
     * norms of the current page ranks and the previous page ranks.
     */
    protected void addRandomJumpProbabilities(DoubleVector newPageRanks,
                                              DoubleVector pageRanks,
                                              DoubleVector initialRanks) {
        // Compute the difference between the l1 norms of the old page ranks
        // and the new ranks.
        double pageRanksSum = 0; 
        double newPageRanksSum = 0;
        for(int i = 0; i < pageRanks.length(); i++) {
            pageRanksSum += Math.abs(pageRanks.get(i));
            newPageRanksSum += Math.abs(newPageRanks.get(i));
        }

        // Add in the weight given to the random jump probabilities.
        double pageRankDifference =
            randomJumpWeight * (pageRanksSum - newPageRanksSum);
        for(int i = 0; i < pageRanks.length(); i++) 
            newPageRanks.add(i, pageRankDifference * initialRanks.get(i));
    }

    /**
     * {@inherDoc}
     */
    public DoubleVector defaultRanks(SparseMatrix affinityMatrix) {
        DoubleVector initialRanks = new DenseVector(affinityMatrix.rows());
        for(int i = 0; i < affinityMatrix.rows(); i++) 
            initialRanks.set(i, 1d/affinityMatrix.rows());
        return initialRanks;
    }
}