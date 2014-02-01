package pl.project13.scala.akka.raft.cluster

import akka.remote.testkit.{MultiNodeSpec, MultiNodeConfig}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import akka.testkit.ImplicitSender
import concurrent.duration._
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import akka.cluster.Cluster
import akka.actor.Props
import pl.project13.scala.akka.raft.example.cluster.WordConcatClusterRaftActor
import pl.project13.scala.akka.raft.example.AppendWord

object RaftClusterConfig extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")

  commonConfig(
    ConfigFactory.parseResources("cluster.conf")
      .withFallback(ConfigFactory.load())
  )
}

abstract class ClusterElectionSpec extends MultiNodeSpec(RaftClusterConfig)
  with FlatSpecLike with Matchers with BeforeAndAfterAll
  with ImplicitSender {

  def initialParticipants = 3

  behavior of s"Leader election on cluster of $initialParticipants nodes"

  import RaftClusterConfig._

  it should "elect a leader" in within(20.seconds) {
    Cluster(system).subscribe(testActor, classOf[MemberUp])
    expectMsgClass(classOf[CurrentClusterState])

    val firstAddress = node(first).address
    val secondAddress = node(second).address
    val thirdAddress = node(third).address

    Cluster(system) join firstAddress

    val members = (1 to initialParticipants) map { n =>
      system.actorOf(Props[WordConcatClusterRaftActor], s"member-$n")
    }

    receiveN(3).collect { case MemberUp(m) => m.address }.toSet should be(
      Set(firstAddress, secondAddress, thirdAddress)
    )

    Cluster(system).unsubscribe(testActor)

    testConductor.enter("all-up")

    Thread.sleep(5000)

    members(0) ! AppendWord("I")
  }

}

class ClusterElectionSpecMultiJvmNode1 extends ClusterElectionSpec
class ClusterElectionSpecMultiJvmNode2 extends ClusterElectionSpec
class ClusterElectionSpecMultiJvmNode3 extends ClusterElectionSpec