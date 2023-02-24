package com.dev007.arcloudanchorsample

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.Guideline
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.ar.core.Anchor
import com.google.ar.core.Session
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.arcore.position
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.ar.node.PlacementMode
import io.github.sceneview.math.Scale
import io.github.sceneview.utils.doOnApplyWindowInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainFragment : Fragment(R.layout.fragment_main) {

    private lateinit var sceneView: ArSceneView
    private lateinit var loadingView: View
    private lateinit var hostButton: Button
    private lateinit var resolveButton: Button
    private lateinit var actionButton: ExtendedFloatingActionButton
    private lateinit var prefs: Prefs
    private var lastCloudAnchorNode: ArModelNode? = null

    private var mode = Mode.HOME

    private var isLoading = false
        set(value) {
            field = value
            loadingView.isGone = !value
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        val topGuideline = view.findViewById<Guideline>(R.id.topGuideline)
        topGuideline.doOnApplyWindowInsets { systemBarsInsets ->
            // Add the action bar margin
            val actionBarHeight =
                (requireActivity() as AppCompatActivity).supportActionBar?.height ?: 0
            topGuideline.setGuidelineBegin(systemBarsInsets.top + actionBarHeight)
        }
        val bottomGuideline = view.findViewById<Guideline>(R.id.bottomGuideline)
        bottomGuideline.doOnApplyWindowInsets { systemBarsInsets ->
            // Add the navigation bar margin
            bottomGuideline.setGuidelineEnd(systemBarsInsets.bottom)
        }

        sceneView = view.findViewById(R.id.sceneView)
        sceneView.apply {
            cloudAnchorEnabled = true
            // Move the instructions up to avoid an overlap with the buttons
            instructions.searchPlaneInfoNode.position.y = -0.5f
        }

        loadingView = view.findViewById(R.id.loadingView)

        actionButton = view.findViewById(R.id.actionButton)
        actionButton.setOnClickListener {
            actionButtonClicked()
        }

        hostButton = view.findViewById(R.id.hostButton)
        hostButton.setOnClickListener {
            selectMode(Mode.HOST)
        }

        resolveButton = view.findViewById(R.id.resolveButton)
        resolveButton.setOnClickListener {
            selectMode(Mode.RESOLVE)
        }

        isLoading = false
        loadArModelNode()
    }

    private fun loadArModelNode(): ArModelNode {
//        isLoading = true
        lastCloudAnchorNode = ArModelNode(placementMode = PlacementMode.PLANE_HORIZONTAL).apply {
            parent = sceneView
            isSmoothPoseEnable = false
            isVisible = true
            modelScale = Scale(0.5f)
            loadModelGlbAsync(
                context = requireContext(),
                lifecycle = lifecycle,
                glbFileLocation = "models/spiderbot.glb",
            ) {
                isLoading = false
            }
        }
        return lastCloudAnchorNode!!
    }

    private fun actionButtonClicked() {
        when (mode) {
            Mode.HOME -> {}
            Mode.HOST -> {
                val frame = sceneView.currentFrame ?: return
                if (lastCloudAnchorNode?.cloudAnchorTaskInProgress ?: false) {
                    Toast.makeText(
                        context,
                        "Task Already Progress.. please wait",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }

                if ((sceneView.arSession?.isTrackingPlane == true) && sceneView.arSession?.estimateFeatureMapQualityForHosting(
                        frame.camera.pose
                    ) == Session.FeatureMapQuality.INSUFFICIENT
                ) {
                    Toast.makeText(context, R.string.insufficient_visual_data, Toast.LENGTH_LONG)
                        .show()
                    return
                }

                val cloudAnchorNode = if (lastCloudAnchorNode != null) lastCloudAnchorNode else {
                    loadArModelNode()
                }

                if (!cloudAnchorNode!!.isAnchored) {
                    cloudAnchorNode.anchor()
                }

                lastCloudAnchorNode = cloudAnchorNode
                lastCloudAnchorNode?.hostCloudAnchor { anchor: Anchor, success: Boolean ->
                    if (success) {
                        prefs.addNode(anchor.cloudAnchorId)
                        Log.d(
                            TAG,
                            "actionButtonClicked: ${anchor.cloudAnchorId} :::${anchor.pose.position}"
                        )
                        loadArModelNode() //preloading for next node.
                        Toast.makeText(context, R.string.node_added, Toast.LENGTH_LONG).show()
                        selectMode(Mode.HOST)
                    } else {
                        Toast.makeText(context, R.string.error_occurred, Toast.LENGTH_LONG).show()
                        Log.d(
                            TAG,
                            "Unable to host the Cloud Anchor. The Cloud Anchor state is ${anchor.cloudAnchorState}"
                        )
                        selectMode(Mode.HOST)
                    }
                    lastCloudAnchorNode = null
                }

                actionButton.apply {
                    setText(R.string.hosting)
                    isEnabled = true
                }
            }
            Mode.RESOLVE -> {

                lifecycleScope.launch(Dispatchers.IO) {
                    val list = prefs.getList()
                    list.forEachIndexed { index, nodeId ->
                        val cloudAnchorNode = withContext(Dispatchers.Main)
                        {
                            ArModelNode(placementMode = PlacementMode.PLANE_HORIZONTAL).apply {
                                parent = sceneView
                                isSmoothPoseEnable = false
                                modelScale = Scale(0.5f)
                                isVisible = false
                                loadModelGlbAsync(
                                    context = requireContext(),
                                    lifecycle = lifecycle,
                                    glbFileLocation = "models/spiderbot.glb"
                                ) {
                                    isLoading = false
                                }
                            }
                        }
                        cloudAnchorNode.resolveCloudAnchor(nodeId) { anchor: Anchor, success: Boolean ->
                            if (success) {
                                cloudAnchorNode.isVisible = true
                                Log.d(
                                    TAG,
                                    "actionButtonClicked: ${anchor.cloudAnchorId} :::${anchor.pose.position}"
                                )
                                if (index == (list.size - 1)) //once last node.
                                    selectMode(Mode.RESET)
                            } else {
                                Toast.makeText(context, R.string.error_occurred, Toast.LENGTH_LONG)
                                    .show()
                                Log.d(
                                    TAG,
                                    "Unable to resolve the Cloud Anchor. The Cloud Anchor state is ${anchor.cloudAnchorState}"
                                )
                                selectMode(Mode.RESOLVE)
                            }
                        }
                    }
                }

                actionButton.apply {
                    setText(R.string.resolving)
                    isEnabled = false
                }
            }
            Mode.RESET -> {
//                sceneView.children.forEach {
//                    sceneView.removeChild(it)
//                }

                selectMode(Mode.HOME)
            }
        }
    }

    private fun selectMode(mode: Mode) {
        this.mode = mode

        when (mode) {
            Mode.HOME -> {
                hostButton.isVisible = true
                resolveButton.isVisible = true
                actionButton.isVisible = false
//                cloudAnchorNode.isVisible = false
            }
            Mode.HOST -> {
                hostButton.isVisible = false
                resolveButton.isVisible = false
                actionButton.apply {
                    setIconResource(R.drawable.ic_host)
                    setText(R.string.host)
                    isVisible = true
                    isEnabled = true
                }
//                cloudAnchorNode.isVisible = true
            }
            Mode.RESOLVE -> {
                hostButton.isVisible = false
                resolveButton.isVisible = false
                actionButton.apply {
                    setIconResource(R.drawable.ic_resolve)
                    setText(R.string.resolve)
                    isVisible = true
//                    isEnabled = editText.text.isNotEmpty()
                }
            }
            Mode.RESET -> {
                actionButton.apply {
                    setIconResource(R.drawable.ic_reset)
                    setText(R.string.reset)
                    isEnabled = true
                }
            }
        }
    }

    private enum class Mode {
        HOME, HOST, RESOLVE, RESET
    }

    companion object {
        private const val TAG = "MainFragment"
    }
}