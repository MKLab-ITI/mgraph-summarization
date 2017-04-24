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
 * This ranking model genralizes from {@link PageRank} by giving high ranks to
 * nodes that are both well connected and located in distinct regions in a
 * network graph.   
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
 */
public class DivRank extends PageRank {

    /**
     * Creates a new {@link PageRank}. 
     */
    public DivRank(double randomJumpWeight) {
        super(randomJumpWeight);
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
            DoubleVector divRanks = new DenseVector(numRows);

            // Create the DivRanks based on the current page rank score and the
            // original transition matrix.
            for (int r = 0; r < numRows; ++r)
                divRanks.set(r, dotProduct(
                            affinityMatrix.getRowVector(r), pageRanks));

            DoubleVector newPageRanks = new DenseVector(numRows);

            // Compute the ranks based on a random walk through the affinity
            // matrix.  Traditionally, this is done by multiplying the transpose
            // of the affinity matrix by the page rank vector.  Since computing
            // the transpose is costly, both memory wise and access wise, we
            // compute it without doing the transpose directly.
            for(int i = 0; i < numRows; i++) {
                SparseDoubleVector row = affinityMatrix.getRowVector(i);
                for(int j : row.getNonZeroIndices()) {
                    double delta = row.get(j) * pageRanks.get(i) * 
                                   pageRanks.get(j) * 1d/divRanks.get(i);
                    newPageRanks.add(j, delta);
                }
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
     * Returns the dot product between a {@link SparseDoubleVector} and a dense
     * {@link DoubleVector}.
     */
    private double dotProduct(SparseDoubleVector transitionProbabilities,
                              DoubleVector pageRanks) {
        double product = 0;
        for (int i : transitionProbabilities.getNonZeroIndices())
            product += transitionProbabilities.get(i) * pageRanks.get(i);
        return product;
    }
}