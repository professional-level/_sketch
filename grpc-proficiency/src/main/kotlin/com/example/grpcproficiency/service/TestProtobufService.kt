//package com.example.service
//
//import com.example.repository.TestProtobufRepository
//import example.Protobuff
//import io.grpc.stub.StreamObserver
//import net.devh.boot.grpc.server.service.GrpcService
//import org.springframework.beans.factory.annotation.Autowired
//
//@GrpcService
//class TestServiceImpl(@Autowired private val repository: TestProtobufRepository) : Protobuff.TestEntityService.TestServiceImplBase() {
//    override fun getOneProto(request: Protobuff.EmptyRequest, responseObserver: StreamObserver<Protobuff.TestEntity>) {
//        val entity = repository.findOne() ?: Protobuff.TestEntity.getDefaultInstance()
//        responseObserver.onNext(entity)
//        responseObserver.onCompleted()
//        Protobuff.
//        )
//    }
//
//    override fun getAllProto(request: Protobuff.EmptyRequest, responseObserver: StreamObserver<Protobuff.TestEntityList>) {
//        val entities = repository.findAll().values
//        val response = Protobuff.TestEntityList.newBuilder().addAllEntities(entities).build()
//        responseObserver.onNext(response)
//        responseObserver.onCompleted()
//    }
//
//    fun temp() {
//
//    }
//}
