/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.sihsalusinterop.api.dao;

import org.openmrs.module.sihsalusinterop.api.model.InteropQueueItem;
import org.springframework.stereotype.Repository;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;

import java.util.List;

/**
 * DAO para InteropQueueItem
 */
@Repository("sihsalusinterop.InteropQueueDao")
public class InteropQueueDao {
	
	private SessionFactory sessionFactory;
	
	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}
	
	public InteropQueueItem save(InteropQueueItem item) {
		sessionFactory.getCurrentSession().saveOrUpdate(item);
		return item;
	}
	
	public InteropQueueItem getById(Integer id) {
		return (InteropQueueItem) sessionFactory.getCurrentSession()
				.get(InteropQueueItem.class, id);
	}
	
	@SuppressWarnings("unchecked")
	public List<InteropQueueItem> getPendingItems() {
		return sessionFactory.getCurrentSession()
				.createCriteria(InteropQueueItem.class)
				.add(Restrictions.eq("status", "PENDING"))
				.list();
	}
	
	@SuppressWarnings("unchecked")
	public List<InteropQueueItem> getAll() {
		return sessionFactory.getCurrentSession()
				.createCriteria(InteropQueueItem.class)
				.list();
	}
	
	public void delete(InteropQueueItem item) {
		sessionFactory.getCurrentSession().delete(item);
	}
}








