package com.example.minimaltodo.testutil

import com.example.minimaltodo.data.local.TodoEntity
import com.example.minimaltodo.data.local.TodoType

private var counter = 0L

fun todo(
    id: String,
    title: String = id,
    parent: String? = null,
    type: TodoType = TodoType.TODO,
    completed: Boolean = false,
    sort: Long = ++counter * 1000,
    pinned: Boolean = false,
    content: String? = null,
    createdAt: Long = sort,
    collapsed: Boolean = false,
) = TodoEntity(
    id = id,
    parentId = parent,
    type = type,
    title = title,
    content = content,
    completed = completed,
    pinned = pinned,
    sortOrder = sort,
    createdAt = createdAt,
    updatedAt = createdAt,
    collapsed = collapsed,
)

fun group(id: String, title: String = id, parent: String? = null, sort: Long = ++counter * 1000, collapsed: Boolean = false) =
    todo(id, title, parent, TodoType.GROUP, sort = sort, collapsed = collapsed)

fun text(id: String, title: String, content: String?, parent: String? = null) =
    todo(id, title, parent, TodoType.TEXT, content = content)
