import sbt._

object Build {
  val as@as@ = Def.settingKey[Seq[Int]]("")
  val bs@bs@ = Def.taskKey[Seq[Int]]("")
  val cs@cs@ = Def.inputKey[Seq[Int]]("")

  val a1@a1@ = as := Seq(1)
  val a2@a2@ = as += 2
  val a3@a3@ = as ++= Seq(3)
  val a4@a4@ = as -= 4
  val a5@a5@ = as --= Seq(5)
  val a6@a6@ = as ~= (_.distinct)

  val b1@b1@ = bs := Seq(1)
  val b2@b2@ = bs += 2
  val b3@b3@ = bs ++= Seq(3)
  val b4@b4@ = bs -= 4
  val b5@b5@ = bs --= Seq(5)
  val b6@b6@ = bs ~= (_.distinct)

  val c1@c1@ = cs := Seq(1)
  val c2@c2@ = cs ~= (_.distinct)

  val s1@s1@ = Def.setting(1)
  val t2@t2@ = Def.task(2)
  val t3@t3@ = Def.taskDyn(Def.task(3))
  val i4@i4@ = Def.inputTask(4)
  val i5@i5@ = Def.inputTaskDyn(Def.task(5))

  val v1@v1@ = as := as.value
  val v2@v2@ = bs := bs.value
//val v3@v3@ = Def.inputTask(cs.value)

  val tv@tv@ = Def.setting(bs.taskValue)

//val itv@itv@ = Def.inputTask(cs.inputTaskValue)

  val p@p@ = Def.inputTask(cs.parsed)

  val e@e@ = Def.inputTask(cs.evaluated)
}
