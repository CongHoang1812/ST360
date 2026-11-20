package com.funny.rank.filters.ui.result

import com.funny.rank.filters.ui.common.navigateSafe
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.funny.rank.filters.R
import com.funny.rank.filters.data.FilterDto
import com.funny.rank.filters.data.assetUri
import com.funny.rank.filters.data.video.VideoLibrary
import com.funny.rank.filters.databinding.FragmentSuccessBinding
import com.funny.rank.filters.ui.camera.CameraViewModel
import com.funny.rank.filters.ui.common.applySystemBarsPadding
import com.funny.rank.filters.ui.filter.FilterAdapter
import com.funny.rank.filters.ui.filter.FilterCardItem
import com.funny.rank.filters.ui.home.HomeFragment
import kotlinx.coroutines.launch

/**
 * Successful (Figma 14:451): ảnh thu nhỏ video, Download (chép ra thư viện ảnh của máy), Share to
 * (Facebook, Instagram, Messenger, WhatsApp, LINE, TikTok, "…" = bảng chia sẻ của Android),
 * Try More Filters (bấm để quay tiếp với filter khác), nút Home.
 */
class SuccessFragment : Fragment() {

    private var _binding: FragmentSuccessBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SuccessViewModel by viewModels()
    private lateinit var filterAdapter: FilterAdapter

    /** Android 9 trở xuống: ghi vào thư viện ảnh cần quyền bộ nhớ. */
    private val requestStorage = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.download() else toast(R.string.success_storage_denied)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSuccessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.content.applySystemBarsPadding(top = true, bottom = true)

        binding.btnHome.setOnClickListener { goHome() }
        binding.btnDownload.setOnClickListener { onDownloadClicked() }
        binding.ivThumbnail.setOnClickListener { openVideo() }
        setUpShareRow()

        filterAdapter = FilterAdapter(
            onClick = ::tryFilter,
            itemWidthPx = resources.getDimensionPixelSize(R.dimen.success_filter_cell),
        )
        binding.rvFilters.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvFilters.adapter = filterAdapter
        val gap = resources.getDimensionPixelSize(R.dimen.success_filter_gap)
        binding.rvFilters.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                if (parent.getChildAdapterPosition(view) > 0) outRect.left = gap
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.events.collect { e -> if (e is SuccessEvent.Message) toast(e.textRes) } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun render(ui: SuccessUi) {
        ui.thumbnail?.let(binding.ivThumbnail::setImageBitmap)
        binding.btnDownload.isEnabled = !ui.isDownloading && !ui.isDownloaded
        binding.btnDownload.alpha = if (ui.isDownloaded) DOWNLOADED_ALPHA else 1f
        binding.tvDownload.setText(if (ui.isDownloaded) R.string.success_downloaded_button else R.string.success_download)
        binding.downloadProgress.isVisible = ui.isDownloading
        filterAdapter.submitList(ui.filters.map { it.toCard() })
    }

    private fun onDownloadClicked() {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) requestStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else viewModel.download()
    }

    // ---------------------------------------------------------------- Chia sẻ

    private class ShareTarget(val view: ImageView, val packages: List<String>)

    private fun setUpShareRow() {
        listOf(
            ShareTarget(binding.shareFacebook, listOf("com.facebook.katana")),
            ShareTarget(binding.shareInstagram, listOf("com.instagram.android")),
            ShareTarget(binding.shareMessenger, listOf("com.facebook.orca")),
            ShareTarget(binding.shareWhatsapp, listOf("com.whatsapp")),
            ShareTarget(binding.shareLine, listOf("jp.naver.line.android")),
            // TikTok có hai gói tùy khu vực.
            ShareTarget(binding.shareTiktok, listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")),
        ).forEach { target -> target.view.setOnClickListener { share(target.packages) } }
        binding.shareMore.setOnClickListener { share(emptyList()) }
    }

    /** Gửi video thẳng tới app trong [packages] nếu đã cài; không có app nào thì mở bảng chia sẻ của Android. */
    private fun share(packages: List<String>) {
        val file = viewModel.state.value.video ?: return
        val uri = VideoLibrary.shareUri(requireContext(), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        for (pkg in packages) {
            try {
                startActivity(Intent(send).setPackage(pkg))
                return
            } catch (_: ActivityNotFoundException) {
                // Chưa cài app này: thử gói tiếp theo, hết thì mở bảng chia sẻ.
            }
        }
        startActivity(Intent.createChooser(send, getString(R.string.success_share_title)))
    }

    /** Bấm ảnh thu nhỏ: mở video bằng trình phát của máy (Gallery của app làm ở giai đoạn 7). */
    private fun openVideo() {
        val file = viewModel.state.value.video ?: return
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(VideoLibrary.shareUri(requireContext(), file), "video/mp4")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(view)
        } catch (_: ActivityNotFoundException) {
            toast(R.string.success_no_player)
        }
    }

    // ---------------------------------------------------------------- Điều hướng

    /** Try More Filters: quay tiếp với filter đó. Back từ Camera về Home (action có popUpTo Home). */
    private fun tryFilter(item: FilterCardItem) {
        val nav = findNavController()
        // Back từ Camera về Home thì Home cuộn lên đầu màn (giống nút Home ở đây).
        runCatching { nav.getBackStackEntry(R.id.homeFragment) }.getOrNull()
            ?.savedStateHandle?.set(HomeFragment.KEY_SCROLL_TO_TOP, true)
        nav.navigateSafe(
            R.id.action_success_to_camera,
            bundleOf(
                CameraViewModel.ARG_FILTER_ID to item.id,
                CameraViewModel.ARG_IS_USER_FILTER to item.isUserFilter,
            ),
        )
    }

    private fun goHome() {
        val nav = findNavController()
        // Về Home thì cuộn lên đầu màn (không giữ vị trí cuộn cũ).
        runCatching { nav.getBackStackEntry(R.id.homeFragment) }.getOrNull()
            ?.savedStateHandle?.set(HomeFragment.KEY_SCROLL_TO_TOP, true)
        if (!nav.popBackStack(R.id.homeFragment, false)) nav.navigateUp()
    }

    private fun FilterDto.toCard() = FilterCardItem(id = id, title = title, coverUri = assetUri(avatar), isUserFilter = false)

    private fun toast(textRes: Int) = Toast.makeText(requireContext(), textRes, Toast.LENGTH_SHORT).show()

    private companion object {
        const val DOWNLOADED_ALPHA = 0.6f
    }
}
