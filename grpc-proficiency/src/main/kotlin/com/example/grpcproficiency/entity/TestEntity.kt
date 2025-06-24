package com.example.grpcproficiency.entity

import example.Protobuff
import kotlin.random.Random

class TestEntity(
    val id: Int,
    val address: Address,
    val members: List<Member>,
    val products: List<Product>,
) {
    data class Address(
        val street: String,
        val city: String,
        val state: String,
        val zipCode: String,
        val country: String,
    )

    data class Member(
        val name: String,
        val memberId: Int,
    )

    data class Product(
        val productId: Int,
        val name: String,
        val price: Double,
        val quantity: Int,
    )

    companion object {
        fun generateDummyData(testEntitySize: Int, memberSize: Int, productSize: Int): List<Protobuff.TestEntity> {
            val testEntities = mutableListOf<Protobuff.TestEntity>()

            for (i in 1..testEntitySize) {
                val testEntity = Protobuff.TestEntity.newBuilder()
                    .setId(i)
                    .setAddress(generateRandomAddress())
                    .addAllMembers(generateRandomMembers(memberSize))
                    .addAllProducts(generateRandomProducts(productSize))
                    .build()

                testEntities.add(testEntity)
            }

            return testEntities
        }

        fun generateRandomAddress(): Protobuff.TestEntity.Address {
            return Protobuff.TestEntity.Address.newBuilder()
                .setStreet("Street ${Random.nextInt(1, 100)}")
                .setCity("City ${Random.nextInt(1, 50)}")
                .setState("State ${Random.nextInt(1, 50)}")
                .setZipCode(Random.nextInt(10000, 99999).toString())
                .setCountry("Country ${Random.nextInt(1, 10)}")
                .build()
        }

        fun generateRandomMembers(size: Int): List<Protobuff.TestEntity.Member> {
            val members = mutableListOf<Protobuff.TestEntity.Member>()
            for (i in 1..size) {
                val member = Protobuff.TestEntity.Member.newBuilder()
                    .setName("Member $i")
                    .setMemberId(i)
                    .build()
                members.add(member)
            }
            return members
        }

        fun generateRandomProducts(size: Int): List<Protobuff.TestEntity.Product> {
            val products = mutableListOf<Protobuff.TestEntity.Product>()
            for (i in 1..size) {
                val product = Protobuff.TestEntity.Product.newBuilder()
                    .setProductId(i)
                    .setName("Product $i")
                    .setPrice(Random.nextDouble(1.0, 100.0))
                    .setQuantity(Random.nextInt(1, 100))
                    .build()
                products.add(product)
            }
            return products
        }

        fun kotlinToProto(testEntityData: TestEntity): Protobuff.TestEntity {
            val addressBuilder = Protobuff.TestEntity.Address.newBuilder()
                .setStreet(testEntityData.address.street)
                .setCity(testEntityData.address.city)
                .setState(testEntityData.address.state)
                .setZipCode(testEntityData.address.zipCode)
                .setCountry(testEntityData.address.country)

            val memberBuilders = testEntityData.members.map {
                Protobuff.TestEntity.Member.newBuilder()
                    .setName(it.name)
                    .setMemberId(it.memberId)
                    .build()
            }

            val productBuilders = testEntityData.products.map {
                Protobuff.TestEntity.Product.newBuilder()
                    .setProductId(it.productId)
                    .setName(it.name)
                    .setPrice(it.price)
                    .setQuantity(it.quantity)
                    .build()
            }

            return Protobuff.TestEntity.newBuilder()
                .setId(testEntityData.id)
                .setAddress(addressBuilder)
                .addAllMembers(memberBuilders)
                .addAllProducts(productBuilders)
                .build()
        }

        fun protoToKotlin(testEntityProto: Protobuff.TestEntity): TestEntity {
            val addressData = TestEntity.Address(
                street = testEntityProto.address.street,
                city = testEntityProto.address.city,
                state = testEntityProto.address.state,
                zipCode = testEntityProto.address.zipCode,
                country = testEntityProto.address.country,
            )

            val members = testEntityProto.membersList.map {
                TestEntity.Member(
                    name = it.name,
                    memberId = it.memberId,
                )
            }

            val products = testEntityProto.productsList.map {
                TestEntity.Product(
                    productId = it.productId,
                    name = it.name,
                    price = it.price,
                    quantity = it.quantity,
                )
            }

            return TestEntity(
                id = testEntityProto.id,
                address = addressData,
                members = members,
                products = products,
            )
        }
    }
}
