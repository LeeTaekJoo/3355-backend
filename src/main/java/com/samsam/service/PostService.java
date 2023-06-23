package com.samsam.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.samsam.repository.FollowRepository;
import com.samsam.repository.GoodRepository;
import com.samsam.repository.PostRepository;
import com.samsam.repository.PostTagRepository;
import com.samsam.repository.TagRepository;
import com.samsam.repository.UserRepository;
import com.samsam.vo.PostTagVO;
import com.samsam.vo.PostVO;
import com.samsam.vo.TagVO;
import com.samsam.vo.UserVO;

@Service
@Transactional
public class PostService {
	@Autowired
	private S3Uploader s3uploader;
	@Autowired
	UserRepository userRepo;
	@Autowired
	PostRepository postRepo;
	@Autowired
	TagRepository tagRepo;
	@Autowired
	PostTagRepository posttagRepo;
	@Autowired
	FollowRepository followRepo;
	@Autowired
	GoodRepository goodRepo;
	
	//내 Post만 불러오기
	public List<Object> myPost(int userNo){
		
		List<Object> result = new ArrayList<>();
		
		UserVO user = userRepo.findById(userNo).get();
		//포스트 찾기
		List<PostVO> myPostList = postRepo.findByUserOrderByPostDateDesc(user);
		
		for(PostVO post : myPostList) {
			HashMap<String, Object> viewpost = new HashMap<>();
			viewpost.put("post", post);
			viewpost.put("goodsCount", goodRepo.findByGoodsCount(post.getPostNo()));
			result.add(viewpost);
		}
		
		return result;
	}
	
	//나와 내가 팔로우 하는 사람들의 Post 불러오기
	public List<Object> mainPost(int userNo){
		List<Object> result = new ArrayList<>();
		
		UserVO user = userRepo.findById(userNo).get();
		
		List<Integer> myFoloowerList = followRepo.findByFollowStart(user.getUserNo());
		myFoloowerList.add(user.getUserNo());//[1,2,3]
		
		List<UserVO> myFoloowerUserList = new ArrayList<UserVO>();
		
		for(int f_num: myFoloowerList) {
			UserVO f_user = userRepo.findById(f_num).get();
			myFoloowerUserList.add(f_user);
		}
		
		List<PostVO> followerPosts = postRepo.findByUserInOrderByPostDateDesc(myFoloowerUserList);
		for(PostVO post: followerPosts) {
			HashMap<String, Object> viewpost = new HashMap<>();
			viewpost.put("post", post);
			viewpost.put("goodsCount", goodRepo.findByGoodsCount(post.getPostNo()));
			result.add(viewpost);
		}
		
		return result;
	}
	
	//S3에 Post 이미지 업로드 + Tag 업로드
	public Integer uploadPost(
			MultipartFile[] images, 
			int userNo,
			String[] tagList) throws IOException{
		System.out.println("Upload S3 Images with post");
		//user를 찾아
		UserVO user = userRepo.findById(userNo).get();
		//post를 생성
		PostVO post = new PostVO();
		String image_list = "[";
		for(MultipartFile image:images) {
			if(image!=null && !image.isEmpty()) {
				String sotredFileName = s3uploader.upload(image, "post");
				image_list += (sotredFileName + ",");
			}
		}
		//마지막에 ,빼주기
		image_list = image_list.substring(0, image_list.length()-1);
		post.setPostImg(image_list+"]");
		post.setUser(user);
		
		//tagList DB 저장
		//없는 tag면 새로 생성
		//있는 tag면 count +1
		for(String tag: tagList) {
			TagVO currentTag = tagRepo.findByTagContent(tag);
			if(currentTag!=null) { //null이 아니면 존재
				currentTag.setTagCount(currentTag.getTagCount() + 1); //tag 개수 증가
			}else { //null이면 아예 없다는 뜻이니까
				currentTag = TagVO.builder()
						.tagContent(tag)
						.tagCount(1)
						.build();
			}
			tagRepo.save(currentTag);
		}
		
		//post는 두번째 save
		PostVO savePost = postRepo.save(post);
		
		
		//posttag save
		for(String tag: tagList) {
			TagVO currentTag = tagRepo.findByTagContent(tag);
			PostTagVO posttag = PostTagVO.builder()
					.post(post)
					.tag(currentTag)
					.build();
			posttagRepo.save(posttag);
		}
		
		
		return savePost.getPostNo();
	}
}
