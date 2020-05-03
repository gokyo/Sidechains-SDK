package com.horizen.chain

import com.horizen.block.SidechainBlock
import com.horizen.utils.{WithdrawalEpochInfo, WithdrawalEpochInfoSerializer}
import com.horizen.vrf.{VrfProofHash, VrfProofHashSerializer}
import scorex.core.NodeViewModifier
import scorex.core.block.Block
import scorex.core.consensus.ModifierSemanticValidity
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}
import scorex.util.{ModifierId, bytesToId, idToBytes}

import scala.collection.mutable.ArrayBuffer

case class SidechainBlockInfo(height: Int,
                              score: Long,
                              parentId: ModifierId,
                              timestamp: Block.Timestamp,
                              semanticValidity: ModifierSemanticValidity,
                              mainchainHeaderHashes: Seq[MainchainHeaderHash],
                              mainchainReferenceDataHeaderHashes: Seq[MainchainHeaderHash],
                              withdrawalEpochInfo: WithdrawalEpochInfo,
                              vrfProofHash: VrfProofHash,
                              lastBlockInPreviousConsensusEpoch: ModifierId
                             ) extends BytesSerializable with LinkedElement[ModifierId] {

  override def getParentId: ModifierId = parentId

  override type M = SidechainBlockInfo

  override lazy val serializer: ScorexSerializer[SidechainBlockInfo] = SidechainBlockInfoSerializer

  override def bytes: Array[Byte] = SidechainBlockInfoSerializer.toBytes(this)
}

object SidechainBlockInfo {
  def mainchainHeaderHashesFromBlock(sidechainBlock: SidechainBlock): Seq[MainchainHeaderHash] = {
    sidechainBlock.mainchainHeaders.map(header => byteArrayToMainchainHeaderHash(header.hash))
  }

  def mainchainReferenceDataHeaderHashesFromBlock(sidechainBlock: SidechainBlock): Seq[MainchainHeaderHash] = {
    sidechainBlock.mainchainBlockReferencesData.map(data => byteArrayToMainchainHeaderHash(data.headerHash))
  }
}

object SidechainBlockInfoSerializer extends ScorexSerializer[SidechainBlockInfo] {
  override def serialize(obj: SidechainBlockInfo, w: Writer): Unit = {
    w.putInt(obj.height)
    w.putLong(obj.score)
    w.putBytes(idToBytes(obj.parentId))
    w.putLong(obj.timestamp)
    w.put(obj.semanticValidity.code)
    w.putInt(obj.mainchainHeaderHashes.size)
    obj.mainchainHeaderHashes.foreach(id => w.putBytes(id.data))
    w.putInt(obj.mainchainReferenceDataHeaderHashes.size)
    obj.mainchainReferenceDataHeaderHashes.foreach(id => w.putBytes(id.data))
    WithdrawalEpochInfoSerializer.serialize(obj.withdrawalEpochInfo, w)

    VrfProofHashSerializer.getSerializer.serialize(obj.vrfProofHash, w)
    w.putBytes(idToBytes(obj.lastBlockInPreviousConsensusEpoch))
  }

  private def readMainchainHeadersHashes(r: Reader): Seq[MainchainHeaderHash] = {
    val references: ArrayBuffer[MainchainHeaderHash] = ArrayBuffer()
    val length = r.getInt()

    (0 until length).foreach(_ => {
      val bytes = r.getBytes(mainchainHeaderHashSize)
      references.append(byteArrayToMainchainHeaderHash(bytes))
    })

    references
  }

  override def parse(r: Reader): SidechainBlockInfo = {
    val height = r.getInt()
    val score = r.getLong()
    val parentId = bytesToId(r.getBytes(NodeViewModifier.ModifierIdSize))
    val timestamp = r.getLong()
    val semanticValidityCode = r.getByte()
    val mainchainHeaderHashes = readMainchainHeadersHashes(r)
    val mainchainReferenceDataHeaderHashes = readMainchainHeadersHashes(r)
    val withdrawalEpochInfo = WithdrawalEpochInfoSerializer.parse(r)

    val vrfProofHash = VrfProofHashSerializer.getSerializer.parse(r)
    val lastBlockInPreviousConsensusEpoch = bytesToId(r.getBytes(NodeViewModifier.ModifierIdSize))

    SidechainBlockInfo(height, score, parentId, timestamp, ModifierSemanticValidity.restoreFromCode(semanticValidityCode),
      mainchainHeaderHashes, mainchainReferenceDataHeaderHashes, withdrawalEpochInfo, vrfProofHash, lastBlockInPreviousConsensusEpoch)
  }
}
