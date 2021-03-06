package com.zego.livedemo3.ui.activities;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;

import com.zego.biz.BizStream;
import com.zego.biz.BizUser;
import com.zego.livedemo3.R;
import com.zego.livedemo3.constants.IntentExtra;
import com.zego.livedemo3.interfaces.OnLiveRoomListener;
import com.zego.livedemo3.presenters.BizLivePresenter;
import com.zego.livedemo3.ui.base.BaseLiveActivity;
import com.zego.livedemo3.ui.widgets.ViewLive;
import com.zego.livedemo3.utils.BizLiveRoomUitl;
import com.zego.livedemo3.utils.PreferenceUtil;
import com.zego.livedemo3.utils.ZegoAVKitUtil;
import com.zego.zegoavkit2.ZegoAVKitCommon;
import com.zego.zegoavkit2.entity.ZegoUser;

import java.util.ArrayList;
import java.util.List;

import butterknife.OnClick;

/**
 * Copyright © 2016 Zego. All rights reserved.
 * des:
 */
public class PublishActivity extends BaseLiveActivity {

    protected AlertDialog mDialogHandleRequestPublish = null;

    /**
     * 启动入口.
     *
     * @param activity     源activity
     * @param publishTitle 视频标题
     */
    public static void actionStart(Activity activity, String publishTitle, boolean enableFrontCam, boolean enableTorch, int selectedBeauty, int selectedFilter) {
        Intent intent = new Intent(activity, PublishActivity.class);
        intent.putExtra(IntentExtra.PUBLISH_TITLE, publishTitle);
        intent.putExtra(IntentExtra.ENABLE_FRONT_CAM, enableFrontCam);
        intent.putExtra(IntentExtra.ENABLE_TORCH, enableTorch);
        intent.putExtra(IntentExtra.SELECTED_BEAUTY, selectedBeauty);
        intent.putExtra(IntentExtra.SELECTED_FILTER, selectedFilter);
        activity.startActivity(intent);
    }

    @Override
    protected void initExtraData(Bundle savedInstanceState) {
        if (savedInstanceState == null) {

            Intent intent = getIntent();
            mPublishTitle = intent.getStringExtra(IntentExtra.PUBLISH_TITLE);
            mEnableFrontCam = intent.getBooleanExtra(IntentExtra.ENABLE_FRONT_CAM, false);
            mEnableTorch = intent.getBooleanExtra(IntentExtra.ENABLE_TORCH, false);
            mSelectedBeauty = intent.getIntExtra(IntentExtra.SELECTED_BEAUTY, 0);
            mSelectedFilter = intent.getIntExtra(IntentExtra.SELECTED_FILTER, 0);
        }

        super.initExtraData(savedInstanceState);
    }

    @Override
    protected void initViews(Bundle savedInstanceState) {
        super.initViews(savedInstanceState);

        // 提前预览, 提升用户体验
        ViewLive freeViewLive = getFreeViewLive();
        if (freeViewLive != null) {
            mZegoAVKit.setLocalView(freeViewLive.getTextureView());
            mZegoAVKit.startPreview();
            mZegoAVKit.setLocalViewMode(ZegoAVKitCommon.ZegoVideoViewMode.ScaleAspectFill);
        }

        mZegoAVKit.setFrontCam(mEnableFrontCam);
        mZegoAVKit.enableTorch(mEnableTorch);
        mZegoAVKit.enableMic(mEnableMic);

        mZegoAVKit.enableBeautifying(ZegoAVKitUtil.getZegoBeauty(mSelectedBeauty));
        mZegoAVKit.setFilter(ZegoAVKitUtil.getZegoFilter(mSelectedFilter));
    }

    @Override
    protected void doBusiness(Bundle savedInstanceState) {
        super.doBusiness(savedInstanceState);

        if (savedInstanceState == null) {
             // 创建业务房间
            BizLivePresenter.getInstance().createAndLoginRoom();
        }

        BizLivePresenter.getInstance().setLiveRoomListener(new OnLiveRoomListener() {
            @Override
            public void onLoginRoom(int errCode, long roomKey, long serverKey) {
                if (errCode == 0) {
                    // 获取channel
                    mChannel = BizLiveRoomUitl.getChannel(roomKey, serverKey);

                    // 成功登陆业务房间后, 开始创建流
                    BizLivePresenter.getInstance().createStream(mPublishTitle, mPublishStreamID);
                    // 打印log
                    recordLog(MY_SELF + ": onLoginRoom success(" + roomKey + ")");
                } else {
                    recordLog(MY_SELF + ": onLoginRoom fail(" + roomKey + ") --errCode:" + errCode);
                }
            }

            @Override
            public void onDisconnected(int errCode, long roomKey, long serverKey) {
                recordLog(MY_SELF + ": onDisconnected");
            }

            @Override
            public void onStreamCreate(String streamID, String url) {
                recordLog(MY_SELF + ": onStreamCreate(" + streamID + ")");
                if (!TextUtils.isEmpty(streamID)) {
                    mPublishStreamID = streamID;
                    if (!mHaveLoginedChannel) {
                        ZegoUser zegoUser = new ZegoUser(PreferenceUtil.getInstance().getUserID(), PreferenceUtil.getInstance().getUserName());
                        mZegoAVKit.loginChannel(zegoUser, mChannel);
                    } else {
                        startPublish();
                    }
                }
            }

            @Override
            public void onStreamAdd(BizStream[] listStream) {
                if (listStream != null && listStream.length > 0) {
                    for (BizStream bizStream : listStream) {
                        recordLog(bizStream.userName + ": onStreamAdd(" + bizStream.streamID + ")");
                        startPlay(bizStream.streamID, getFreeZegoRemoteViewIndex());
                    }
                }
            }

            @Override
            public void onStreamDelete(BizStream[] listStream) {
                if (listStream != null && listStream.length > 0) {
                    for (BizStream bizStream : listStream) {
                        recordLog(bizStream.userName + ": onStreamDelete(" + bizStream.streamID + ")");
                        stopPlay(bizStream.streamID);
                    }
                }
            }

            @Override
            public void onReceiveRequestMsg(String fromUserID, String fromUserName, final String magic) {
                // 打印log
                recordLog(getString(R.string.someone_is_requesting_to_broadcast, fromUserName));

                BizUser toUser = new BizUser();
                toUser.userID = fromUserID;
                toUser.userName = fromUserName;
                final List<BizUser> listToUsers = new ArrayList<>();
                listToUsers.add(toUser);

                // 弹出对话框
                mDialogHandleRequestPublish = new AlertDialog.Builder(PublishActivity.this).setTitle(getString(R.string.hint))
                        .setMessage(getString(R.string.someone_is_requesting_to_broadcast_allow, fromUserName))
                        .setPositiveButton(getString(R.string.Allow), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // 同意连麦请求
                                BizLivePresenter.getInstance().respondLiveTogether(listToUsers, magic, true);
                                dialog.dismiss();
                            }
                        }).setNegativeButton(getString(R.string.Deny), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // 拒绝连麦请求
                                BizLivePresenter.getInstance().respondLiveTogether(listToUsers, magic, false);
                                dialog.dismiss();
                            }
                        }).create();

                mDialogHandleRequestPublish.show();
            }

            @Override
            public void onReceiveRespondMsg(boolean isRespondToMyRequest, boolean isAgree, String userNameOfRequest) {
                if (!isRespondToMyRequest) {
                    if (isAgree) {
                        // 打印日志
                        recordLog(getString(R.string.request_of_broadcast_has_been_allowed, userNameOfRequest));
                        if (mDialogHandleRequestPublish != null && mDialogHandleRequestPublish.isShowing()) {
                            mDialogHandleRequestPublish.dismiss();
                        }
                    } else {
                        recordLog(getString(R.string.request_of_broadcast_has_been_denied, userNameOfRequest));
                    }
                }
            }
        }, mHandler);
    }

    @Override
    protected void doPublishOrPlay() {
        startPublish();
    }

    @Override
    protected void initPublishControlText() {
        if (mIsPublishing) {
            tvPublisnControl.setText(R.string.stop_publishing);
            tvPublishSetting.setEnabled(true);
        } else {
            tvPublisnControl.setText(R.string.start_publishing);
            tvPublishSetting.setEnabled(false);
        }
    }

    @Override
    protected void hidePlayBackground() {

    }

    @OnClick(R.id.tv_publish_control)
    public void doPublish() {
        if (mIsPublishing) {
            stopPublish();
        } else {
            BizLivePresenter.getInstance().createStream(mPublishTitle, mPublishStreamID);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 清空回调, 避免内存泄漏
        BizLivePresenter.getInstance().setLiveRoomListener(null, null);
    }
}
