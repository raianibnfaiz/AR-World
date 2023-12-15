package com.raian.arworld

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.view.isGone
import androidx.lifecycle.lifecycleScope
import com.google.android.material.internal.ViewUtils.doOnApplyWindowInsets
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.TrackingFailureReason
import com.google.ar.sceneform.rendering.ViewAttachmentManager
import com.google.ar.sceneform.rendering.ViewRenderable
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.getUpdatedPlanes
import io.github.sceneview.ar.getDescription
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.ViewNode
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"
class MainActivity : AppCompatActivity() {
    lateinit var sceneView: ARSceneView
    lateinit var loadingView: View
    lateinit var instructionText: TextView
    lateinit var viewNode: ViewNode
    lateinit var viewAttachmentManager: ViewAttachmentManager
    var isLoading = false
        set(value) {
            field = value
            loadingView.isGone = !value
        }

    var anchorNode: AnchorNode? = null
        set(value) {
            if (field != value) {
                field = value
                updateInstructions()
            }
        }

    var trackingFailureReason: TrackingFailureReason? = null
        set(value) {
            if (field != value) {
                field = value
                updateInstructions()
            }
        }

    fun updateInstructions() {
        instructionText.text = trackingFailureReason?.let {
            it.getDescription(this)
        } ?: if (anchorNode == null) {
            getString(R.string.point_your_phone_down)
        } else {
            null
        }
    }
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        instructionText = findViewById(R.id.instructionText)
        loadingView = findViewById(R.id.loadingView)
        sceneView = findViewById<ARSceneView?>(R.id.sceneView).apply {

            planeRenderer.isEnabled = true
            configureSession { session, config ->
                config.depthMode = when (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    true -> Config.DepthMode.AUTOMATIC
                    else -> Config.DepthMode.DISABLED
                }
                config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            }
            onSessionUpdated = { _, frame ->
                if (anchorNode == null) {
                    frame.getUpdatedPlanes()
                        .firstOrNull { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
                        ?.let { plane ->
                            addAnchorNode(plane.createAnchor(plane.centerPose))
                        }
                }
            }
            onTrackingFailureChanged = { reason ->
                this@MainActivity.trackingFailureReason = reason
            }
        }
        sceneView.setOnTouchListener { _, event ->
            handleTap(event)
            true
        }

    }

  /*  private fun handleTap(event: MotionEvent) {
        if (event.action == MotionEvent.ACTION_DOWN) {
            // Check if a plane was hit
            val frame = sceneView.frame
            val hitResult = frame?.hitTest(event.x, event.y)?.firstOrNull()
            hitResult?.let {
                // Create an anchor at the hit location
                addAnchorNode(hitResult.createAnchor())
            }


        }
    }*/
  private fun handleTap(event: MotionEvent) {
      if (event.action == MotionEvent.ACTION_DOWN) {
          val frame = sceneView.frame
          val hitResult = frame?.hitTest(event.x, event.y)?.firstOrNull()
          hitResult?.let {
              val anchor = hitResult.createAnchor() ?: return
              val anchorNode = AnchorNode(sceneView.engine, anchor).apply { isEditable = true }
              addAnchorNode(hitResult.createAnchor())
              val model = ModelNode(
                  modelInstance = sceneView.modelLoader.createModelInstance("map_marker.glb"),
                  parent = anchorNode,
                  scaleToUnits = 0.004f
              ).apply {
                  position = Position(0.0f, -0.06f, 0.0f)
                  rotation = Rotation(90.0f, 0.0f, 0.0f)
                  isEditable = true
                 // parent?.addChildNode(viewNode)
                  ViewRenderable.builder()
                      .setView(this@MainActivity,R.layout.place_view)
                      .build(sceneView.engine).thenAccept{r: ViewRenderable? ->
                          sceneView.addChildNode  ( ViewNode(
                              engine=sceneView.engine,
                              modelLoader = sceneView.modelLoader,
                              viewAttachmentManager= viewAttachmentManager!!
                          ).apply {
                              setRenderable(r)
                              isEditable = true
                              scale = Scale(5f)

                          })

                      }
              }

            /*  ViewNode(
                  engine = sceneView.engine,
                  modelLoader = sceneView.modelLoader,
                  viewAttachmentManager = ViewAttachmentManager(this@MainActivity, sceneView),
                  parent = model
              ).apply {
                  isEditable = true
                  loadView(this@MainActivity, R.layout.place_view)
                  onViewLoaded = { view, _ ->
                      // Access your TextView from the loaded view here
                      val textView = findViewById<TextView>(R.id.placeName) // Replace 'text_view_id' with your actual TextView ID
                      textView.text = "Custom text based on tap location!" // Set dynamic text based on your logic

                      // Additional customization of the TextView if needed
                      textView.setTextColor(Color.RED)
                      textView.textSize = 36f
                      onViewLoaded = {_, _ ->
                          Log.d(TAG, "View Loaded!")
                      }
                  }
                  position = Position(x = 0.0f, y = 0.2f, z = 0.0f) // Adjust position as needed
              }*/

              sceneView.addChildNode(model)
          }
      }
  }

    fun addAnchorNode(anchor: Anchor) {
        val anchorNode = AnchorNode(sceneView.engine, anchor).apply {
            isEditable = true
            val model =  ModelNode(
                modelInstance = sceneView.modelLoader.createModelInstance("map_marker.glb"),
                // Scale to fit in a 0.5 meters cube
                scaleToUnits = 0.5f,
                // Bottom origin instead of center so the model base is on floor
                //centerOrigin = Position(y = -0.5f)
            ).apply {
                isEditable = true
            }
            viewAttachmentManager = ViewAttachmentManager(this@MainActivity, sceneView)

             ViewRenderable.builder()
                 .setView(this@MainActivity, R.layout.place_view).apply {
                     val textView = findViewById<TextView>(R.id.placeName)
                    // Log.d(TAG, "addAnchorNode: ${textView.text}")
                 }
                .build(engine).thenAccept{r: ViewRenderable? ->
                    addChildNode  ( ViewNode(
                        engine=engine,
                        modelLoader = sceneView.modelLoader,
                        viewAttachmentManager= viewAttachmentManager!!
                    ).apply {
                        setRenderable(r)
                        isEditable = true
                        scale = Scale(5f)

                    })

                }
            //val modelNode = ViewNode(sceneView.engine, sceneView.modelLoader, viewAttachmentManager) .apply {  setRenderable(viewRenderable)  }
            lifecycleScope.launch {
                isLoading = true
                sceneView.modelLoader.loadModelInstance(
                    "map_marker.glb"
                    //https://ssdfceneview.github.io/assets/models/DamagedHelmet.glb
                )?.let { modelInstance ->
                    addChildNode(
                        model
                    )
                }
                isLoading = false
                ViewNode(
                    engine = sceneView.engine,
                    modelLoader = sceneView.modelLoader,
                    viewAttachmentManager = ViewAttachmentManager(this@MainActivity, sceneView),
                    parent = model
                ).apply {
                    isEditable = true
                    loadView(this@MainActivity, R.layout.place_view)
                    onViewLoaded = { view, _ ->
                        // Access your TextView from the loaded view here
                        val textView = findViewById<TextView>(R.id.placeName) // Replace 'text_view_id' with your actual TextView ID
                        //textView.text = "Custom text based on tap location!" // Set dynamic text based on your logic

                        // Additional customization of the TextView if needed
                        textView.setTextColor(Color.RED)
                        textView.textSize = 36f
                        onViewLoaded = {_, _ ->
                            Log.d(TAG, "View Loaded!")
                        }
                    }
                    position = Position(x = 0.0f, y = 0.2f, z = 0.0f) // Adjust position as needed
                }
            }
            // Create and add a text node to the anchor node

            this@MainActivity.anchorNode = this
            parent?.addChildNode(viewNode)
        }
        //sceneView.addChildNode(anchorNode)
        /*// Add the anchor node to the scene
        sceneView.addChildNode(anchorNode)
        sceneView.addChildNode(
            AnchorNode(sceneView.engine, anchor)
                .apply {
                    isEditable = true
                    lifecycleScope.launch {
                        isLoading = true
                        sceneView.modelLoader.loadModelInstance(
                            "https://ssdfceneview.github.io/assets/models/DamagedHelmet.glb"
                        )?.let { modelInstance ->
                            addChildNode(
                                ModelNode(
                                    modelInstance = modelInstance,
                                    // Scale to fit in a 0.5 meters cube
                                    scaleToUnits = 0.5f,
                                    // Bottom origin instead of center so the model base is on floor
                                    centerOrigin = Position(y = -0.5f)
                                ).apply {
                                    isEditable = true
                                }
                            )
                        }
                        isLoading = false
                    }
                    anchorNode = this
                }
        )*/
    }
    // Function to create a text node

}