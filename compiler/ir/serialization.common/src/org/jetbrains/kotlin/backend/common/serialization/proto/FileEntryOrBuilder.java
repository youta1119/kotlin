// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: compiler/ir/serialization.common/src/KotlinIr.proto

package org.jetbrains.kotlin.backend.common.serialization.proto;

public interface FileEntryOrBuilder extends
    // @@protoc_insertion_point(interface_extends:org.jetbrains.kotlin.backend.common.serialization.proto.FileEntry)
    org.jetbrains.kotlin.protobuf.MessageLiteOrBuilder {

  /**
   * <code>required .org.jetbrains.kotlin.backend.common.serialization.proto.String name = 1;</code>
   */
  boolean hasName();
  /**
   * <code>required .org.jetbrains.kotlin.backend.common.serialization.proto.String name = 1;</code>
   */
  org.jetbrains.kotlin.backend.common.serialization.proto.String getName();

  /**
   * <code>repeated int32 line_start_offsets = 2;</code>
   */
  java.util.List<java.lang.Integer> getLineStartOffsetsList();
  /**
   * <code>repeated int32 line_start_offsets = 2;</code>
   */
  int getLineStartOffsetsCount();
  /**
   * <code>repeated int32 line_start_offsets = 2;</code>
   */
  int getLineStartOffsets(int index);
}