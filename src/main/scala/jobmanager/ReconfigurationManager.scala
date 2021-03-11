// package jobmanager

import scala.collection.mutable.ArrayBuffer
import com.google.ortools.Loader
import com.google.ortools.linearsolver.MPConstraint
import com.google.ortools.linearsolver.MPObjective
import com.google.ortools.linearsolver.MPSolver
import com.google.ortools.linearsolver.MPVariable

// import jobmanager.TaskManagerInfo

object ReconfigurationManager {
  def solveILP(
      taskManagers: ArrayBuffer[TaskManagerInfo],
      prl: Float,
      alpha: Float
  ) = {
    val solver = MPSolver.createSolver("SCIP");
    if (solver == null) {
      System.out.println("Could not create solver SCIP")
    } else {
      val m = taskManagers.length
      val infinity = java.lang.Double.POSITIVE_INFINITY
      val p = new Array[MPVariable](m)
      val eqn2 = Array.ofDim[MPConstraint](m, m - 1)
      val eqn3 = Array.ofDim[MPConstraint](m, m - 1)
      val eqn4 = new Array[MPConstraint](m)
      val obj = Array.ofDim[MPObjective](m, m)

      for (i <- 0 until m) {
        p(i) = solver.makeIntVar(0.0, infinity, "p" + taskManagers(i).id)
      }

      //Equation 2
      for (i <- 0 until m) {
        for (j <- 0 until m - 1) {
          eqn2(i)(j) = solver.makeConstraint(
            0,
            alpha * taskManagers(i).bandwidthsToSelf(j).rate,
            "eqn2_" + taskManagers(i)
              .bandwidthsToSelf(j)
              .fromID + "_" + taskManagers(i).id
          )
          for (k <- 0 until m) {
            if (k == i) {
              eqn2(i)(j).setCoefficient(p(k), taskManagers(i).ipRate / prl)
            } else {
              eqn2(i)(j).setCoefficient(p(k), 0)
            }
          }
        }
      }

      //Equation 3
      var tmp: Int = 0;
      for (i <- 0 until m) {
        for (j <- 0 until m) {
          if (j != i) {
            for (k <- 0 until m - 1) {
              if (
                taskManagers(j).bandwidthsToSelf(k).fromID == taskManagers(
                  i
                ).id
              ) {
                eqn3(i)(tmp) = solver.makeConstraint(
                  0,
                  alpha * taskManagers(j).bandwidthsToSelf(k).rate,
                  "eqn3_" + taskManagers(i).id + "_" + taskManagers(k).id
                )
                for (l <- 0 until m) {
                  if (l == i) {
                    eqn3(i)(j)
                      .setCoefficient(p(l), taskManagers(i).opRate / prl)
                  } else {
                    eqn3(i)(j).setCoefficient(p(l), 0)
                  }
                }
                tmp += 1
              }
            }
          }
        }
      }

      //Equation 4
      for (i <- 0 until m) {
        eqn4(i) = solver.makeConstraint(
          0,
          taskManagers(i).numSlots,
          "eqn4_" + taskManagers(i).id
        )
        for (k <- 0 until m) {
          if (k == i) {
            eqn4(i).setCoefficient(p(k), 1)
          } else {
            eqn4(i).setCoefficient(p(k), 0)
          }
        }
      }

      //Equation 5
      val eqn5 = solver.makeConstraint(prl, prl, "eqn5")
      for (i <- 0 until m) {
        eqn5.setCoefficient(p(i), 1)
      }

      //Objective
      for (i <- 0 until m) {
        for (j <- 0 until m) {
          obj(i)(j) = solver.objective();
          for (k <- 0 until m) {
            var l_ik: Float = 0;
            var l_kj: Float = 0;
            for (l <- 0 until m) {
              if (
                taskManagers(k).latenciesToSelf(l).fromID == taskManagers(i).id
              ) {
                l_ik = taskManagers(k).latenciesToSelf(l).time
              }
              if (
                taskManagers(j).latenciesToSelf(l).fromID == taskManagers(k).id
              ) {
                l_kj = taskManagers(j).latenciesToSelf(l).time
              }
            }

            obj(i)(j).setCoefficient(p(k), (l_ik + l_kj));
          }
          obj(i)(j).setMinimization();
        }
      }

      val resultStatus = solver.solve();
    }
  }

}
