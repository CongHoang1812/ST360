package com.example.rankingfilter.ui.makefilter

import com.example.rankingfilter.ui.common.navigateSafe
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import coil3.load
import com.example.rankingfilter.R
import com.example.rankingfilter.data.FilterDto
import com.example.rankingfilter.databinding.FragmentMakeFilterBinding
import com.example.rankingfilter.ui.common.ConfirmDialogFragment
import com.example.rankingfilter.ui.common.GridSpacingDecoration
import com.example.rankingfilter.ui.common.applySystemBarsPadding
import kotlinx.coroutines.launch

/**
 * Make a Filter (Figma 57:247) và Edit filter (29:2091, cùng layout, argument "filter_id").
 * Lưu xong thì quay lại màn trước và báo [RESULT_FILTER_SAVED] để Choose a Filter mở tab My Filters.
 */
class MakeFilterFragment : Fragment() {

    private var _binding: FragmentMakeFilterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MakeFilterViewModel by viewModels()
    private lateinit var adapter: PhotoSlotAdapter

    private val pickPhotos = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(FilterDto.PHOTO_COUNT),
    ) { uris -> viewModel.addPhotos(uris) }

    private val pickCover = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.setCover(uri)
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        viewModel.onCaptureResult(ok)
    }

    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else toast(R.string.camera_permission_denied)
    }

    /** Chặn Back khi đã có thay đổi chưa lưu để hiện popup "Leave without saving?". */
    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = onBackRequested()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMakeFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val spanCount get() = resources.getInteger(R.integer.span_make_filter_photos)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.content.applySystemBarsPadding(top = true)
        binding.scroll.applySystemBarsPadding(bottom = true)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        binding.btnBack.setOnClickListener { onBackRequested() }

        adapter = PhotoSlotAdapter(
            onPhotoClick = viewModel::togglePhoto,
            onRemoveClick = viewModel::removePhoto,
            onAddClick = ::showAddPhotoDialog,
        )
        binding.rvPhotos.layoutManager = GridLayoutManager(requireContext(), spanCount)
        binding.rvPhotos.adapter = adapter
        binding.rvPhotos.itemAnimator = null
        binding.rvPhotos.addItemDecoration(
            GridSpacingDecoration(spanCount, resources.getDimensionPixelSize(R.dimen.photo_cell_gap))
        )

        binding.etName.doAfterTextChanged { viewModel.setName(it?.toString().orEmpty()) }
        binding.btnAddPhotos.setOnClickListener { showAddPhotoDialog() }
        binding.btnChooseCover.setOnClickListener {
            pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.coverCell.setOnClickListener { binding.btnChooseCover.performClick() }
        binding.btnSave.setOnClickListener { viewModel.save() }

        childFragmentManager.setFragmentResultListener(AddPhotoDialogFragment.REQUEST_KEY, viewLifecycleOwner) { _, b ->
            when (AddPhotoDialogFragment.choiceFrom(b)) {
                AddPhotoDialogFragment.Choice.CAMERA -> onTakePhoto()
                AddPhotoDialogFragment.Choice.GALLERY ->
                    pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                null -> Unit
            }
        }
        ConfirmDialogFragment.listen(this, REQUEST_LEAVE) { leave() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.events.collect(::onEvent) }
            }
        }
    }

    private fun render(state: MakeFilterUiState) {
        state.loadedName?.let { name ->
            if (binding.etName.text.toString() != name) {
                binding.etName.setText(name)
                binding.etName.setSelection(name.length)
            }
            viewModel.onNameApplied()
        }

        val count = state.photos.size
        binding.tvPhotosCount.text = getString(R.string.make_filter_photos_count, count, FilterDto.PHOTO_COUNT)
        binding.tvPhotosHint.text = getString(R.string.make_filter_need_photos, FilterDto.MIN_PHOTO_COUNT)
        adapter.submitList(PhotoSlot.build(state.photos, state.selected))
        binding.btnAddPhotos.isEnabled = !state.isFull

        val cover = state.coverToShow
        binding.ivCover.isVisible = cover != null
        binding.ivCoverPlaceholder.isVisible = cover == null
        if (cover != null && binding.ivCover.tag != cover) {
            binding.ivCover.tag = cover
            binding.ivCover.load(cover.toString())
        } else if (cover == null) {
            binding.ivCover.tag = null
            binding.ivCover.setImageDrawable(null)
        }

        binding.btnSave.isEnabled = state.canSave
        binding.busyOverlay.isVisible = state.isLoading || state.isSaving
    }

    private fun onEvent(event: MakeFilterEvent) {
        when (event) {
            is MakeFilterEvent.Saved -> {
                findNavController().previousBackStackEntry?.savedStateHandle?.set(RESULT_FILTER_SAVED, event.filterId)
                if (viewModel.filterId != null) {
                    // Sửa filter có sẵn: Toast "đã cập nhật" rồi quay lại.
                    toast(R.string.make_filter_updated)
                    leave()
                } else {
                    // Tạo mới: sang màn Successful (Figma 57:137), Make a Filter bị gỡ khỏi back stack.
                    backCallback.isEnabled = false
                    findNavController().navigateSafe(
                        R.id.action_makeFilter_to_filterSuccess,
                        bundleOf(FilterSuccessFragment.ARG_FILTER_ID to event.filterId),
                    )
                }
            }
            is MakeFilterEvent.Message -> toast(event.textRes)
            is MakeFilterEvent.TooManyPhotos -> Toast.makeText(
                requireContext(),
                resources.getQuantityString(
                    R.plurals.make_filter_too_many, event.added, event.added, FilterDto.PHOTO_COUNT,
                ),
                Toast.LENGTH_LONG,
            ).show()
            MakeFilterEvent.LoadFailed -> {
                toast(R.string.make_filter_load_error)
                leave()
            }
        }
    }

    private fun showAddPhotoDialog() {
        if (viewModel.state.value.isFull) return
        if (childFragmentManager.findFragmentByTag(AddPhotoDialogFragment.TAG) != null) return
        AddPhotoDialogFragment().show(childFragmentManager, AddPhotoDialogFragment.TAG)
    }

    private fun onTakePhoto() {
        val granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        // App khai báo quyền CAMERA nên phải được cấp thì mới mở được camera hệ thống.
        if (granted) launchCamera() else requestCamera.launch(Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        try {
            takePicture.launch(viewModel.newCaptureUri())
        } catch (e: ActivityNotFoundException) {
            viewModel.onCaptureResult(false)
            toast(R.string.no_camera_app)
        }
    }

    private fun onBackRequested() {
        val state = viewModel.state.value
        if (state.isSaving) return
        if (viewModel.hasChanges) {
            ConfirmDialogFragment.show(
                host = this,
                requestKey = REQUEST_LEAVE,
                title = R.string.leave_title,
                message = R.string.leave_message,
                negative = R.string.leave_keep_editing,
                positive = R.string.leave_confirm,
            )
        } else {
            leave()
        }
    }

    private fun leave() {
        backCallback.isEnabled = false
        findNavController().navigateUp()
    }

    private fun toast(textRes: Int) = Toast.makeText(requireContext(), textRes, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Khóa kết quả trong savedStateHandle của màn trước: id filter vừa lưu. */
        const val RESULT_FILTER_SAVED = "filter_saved"
        private const val REQUEST_LEAVE = "leave_make_filter"
    }
}
