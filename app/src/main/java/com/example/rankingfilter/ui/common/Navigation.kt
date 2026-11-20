package com.example.rankingfilter.ui.common

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.navigation.NavController

/**
 * Chỉ điều hướng khi màn hiện tại có [actionId]. Bấm nhanh hai ô (hoặc hai ngón cùng lúc) thì cú bấm sau
 * chạy khi đã sang màn mới, nơi action này không tồn tại → navigate() thường ném IllegalArgumentException.
 */
fun NavController.navigateSafe(@IdRes actionId: Int, args: Bundle? = null) {
    if (currentDestination?.getAction(actionId) != null) navigate(actionId, args)
}

