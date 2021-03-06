package com.muzima

import grails.plugin.springsecurity.annotation.Secured

import static java.util.Date.parse
import static org.springframework.http.HttpStatus.CREATED
import static org.springframework.http.HttpStatus.NOT_FOUND
import static org.springframework.http.HttpStatus.OK
import grails.transaction.Transactional

@Transactional(readOnly = true)
class AssignmentController {

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def convert(Assignment assignmentInstance) {
        def deviceInstance = assignmentInstance.device
        def personInstance = assignmentInstance.person
        def assignment = [
                id    : assignmentInstance.id,
                device: [
                        id           : deviceInstance.id,
                        imei         : deviceInstance.imei,
                        sim          : deviceInstance.sim,
                        name         : deviceInstance.name,
                        description  : deviceInstance.description,
                        purchasedDate: deviceInstance.purchasedDate.time,
                        status       : deviceInstance.status,
                        deviceType   : [
                                id         : deviceInstance.deviceType.id,
                                name       : deviceInstance.deviceType.name,
                                description: deviceInstance.deviceType.description
                        ]
                ],
                person: [
                        id         : personInstance.id,
                        gender     : personInstance.gender,
                        birthdate  : personInstance.birthdate.time,
                        personNames: personInstance.personNames.collect {
                            [
                                    id        : it.id,
                                    preferred : it.preferred,
                                    prefix    : it.prefix,
                                    givenName : it.givenName,
                                    middleName: it.middleName,
                                    familyName: it.familyName,
                                    degree    : it.degree
                            ]
                        }
                ]
        ]
        return assignment
    }

    @Secured(['isFullyAuthenticated()'])
    def index() {
        def assignmentMap = []
        def assignmentCount = 0
        if (params.containsKey("deviceId") || params.containsKey("personId")) {
            Assignment.createCriteria().listDistinct {
                firstResult: params.offset
                maxResults: params.max
                createAlias("device", "device")
                createAlias("person", "person")
                eq("voided", Boolean.FALSE)
                if (params.containsKey("deviceId")) {
                    eq("device.id", params.long("deviceId"))
                }
                if (params.containsKey("personId")) {
                    eq("person.id", params.long("personId"))
                }
            }.each {
                assignmentMap.add(convert(it))
            }

            assignmentCount = Assignment.createCriteria().get {
                eq("voided", Boolean.FALSE)
                if (params.containsKey("deviceId")) {
                    eq("device.id", params.long("deviceId"))
                }
                if (params.containsKey("personId")) {
                    eq("person.id", params.long("personId"))
                }
                projections {
                    countDistinct("id")
                }
            }
        }
        // use withFormat here if we need to return xml in the future
        render(contentType: "application/json") {
            count = assignmentCount
            results = assignmentMap
        }
    }

    @Secured(['isFullyAuthenticated()'])
    def show() {
        def assignmentInstance = Assignment.get(params.id)
        if (assignmentInstance == null) {
            notFound()
            return
        }
        render(contentType: "application/json") {
            convert(assignmentInstance)
        }
    }

    @Secured(['isFullyAuthenticated()'])
    @Transactional
    def save() {
        def json = request.JSON
        def device = json["device"]
        def person = json["person"]
        def assignmentInstance = new Assignment(
                device: Device.get(device["id"]),
                person: Person.get(person["id"]),
        )
        if (assignmentInstance == null) {
            notFound()
            return
        }

        assignmentInstance.save flush: true, failOnError: true
        response.status = CREATED.value();
        render(contentType: "application/json") {
            convert(assignmentInstance)
        }
    }

    @Secured(['isFullyAuthenticated()'])
    @Transactional
    def update() {
        def json = request.JSON
        def assignmentInstance = Assignment.get(json["id"])
        if (assignmentInstance == null) {
            notFound()
            return
        }

        def jsonAssignment = new Assignment(json)
        assignmentInstance.updateAssignment(jsonAssignment)

        assignmentInstance.save flush: true, failOnError: true
        response.status = OK.value()
        render(contentType: "application/json") {
            convert(assignmentInstance)
        }
    }

    protected void notFound() {
        render status: NOT_FOUND
    }
}
